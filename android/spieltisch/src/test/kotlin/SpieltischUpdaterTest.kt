package com.mediagram.spieltisch

import android.database.Cursor
import android.database.MatrixCursor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import update.ApkInstaller
import update.AppUpdater
import update.UpdateConfig
import update.UpdateStatus
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Spieltisch's updater end to end over a fake media app: what it downloads, installs, and leaves alone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SpieltischUpdaterTest {
    @get:Rule val folder = TemporaryFolder()

    private val resolver = RuntimeEnvironment.getApplication().contentResolver
    private val bytes = ByteArray(5_000) { (it % 251).toByte() }
    private val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private val updates by lazy { File(folder.root, "updates") }
    private val installed = mutableListOf<File>()
    private val core = FakeSpieltischCore()
    private val night = NightSession(core, RecordingAnnouncer(), CoroutineScope(Dispatchers.Unconfined), "", NoMediaApp, NoImages)
    private val model by lazy { GameNightViewModel(night) }
    private val installedAfterStop = mutableListOf<Boolean>()

    private val installer =
        object : ApkInstaller {
            override fun refusal(apk: File): String? = null

            override fun install(apk: File) {
                installed += apk
                installedAfterStop += night.stopping?.isCompleted ?: true
            }
        }

    private val updater by lazy {
        AppUpdater(
            UpdateConfig(enabled = true, installedVersionCode = 175_000, updatesDir = updates),
            MediaReleaseSource(resolver),
            night.asPlayback(),
            installer,
            CoroutineScope(Dispatchers.Unconfined),
        )
    }

    private fun announcing(
        code: Long = 176_000L,
        sha: String = sha256,
    ): FakeMediaApp =
        mediaApp().apply {
            release = { row(code, sha) }
            files = mapOf("/release.apk" to folder.newFile().apply { writeBytes(bytes) })
        }

    private fun row(
        code: Long,
        sha: String,
    ): Cursor = MatrixCursor(arrayOf("version_name", "version_code", "bytes", "sha256")).apply { addRow(arrayOf<Any>("0.176.0", code, bytes.size.toLong(), sha)) }

    /** A foreground, waited out: the check runs on its own thread. */
    private fun foreground(until: (UpdateStatus) -> Boolean = { it != UpdateStatus.NotChecked && it !is UpdateStatus.Downloading }): UpdateStatus {
        updater.onForeground()
        return runBlocking { withTimeout(10_000) { updater.status.first(until) } }
    }

    @Test
    fun nothingNewerLeavesTheAppUpToDate() {
        announcing(code = 175_000L)

        assertTrue(foreground() is UpdateStatus.UpToDate)
        assertEquals(emptyList(), updates.list().orEmpty().toList())
    }

    @Test
    fun noReleasePinnedLeavesTheAppUpToDate() {
        mediaApp().release = { MatrixCursor(arrayOf("version_name", "version_code", "bytes", "sha256")) }

        assertTrue(foreground() is UpdateStatus.UpToDate)
    }

    @Test
    fun aNewerReleaseIsDownloadedAndInstalledWhenTheAppLeaves() {
        announcing()

        assertEquals(UpdateStatus.Ready("0.176.0"), foreground())
        assertEquals(bytes.toList(), File(updates, "176000.apk").readBytes().toList())

        updater.onBackground()
        assertEquals(listOf(File(updates, "176000.apk")), installed)
    }

    @Test
    fun aDownloadThatIsNotTheAnnouncedHashIsDeletedAndNeverInstalled() {
        announcing(sha = "0".repeat(64))

        assertTrue(foreground() is UpdateStatus.Failed)
        assertEquals(emptyList(), updates.list().orEmpty().toList())
        updater.onBackground()
        assertEquals(emptyList(), installed)
    }

    @Test
    fun aDownloadRunsWhileANightRuns() {
        announcing()
        model.start()
        assertEquals(GameNightUi.Ready("http://127.0.0.1:8080/tv"), model.state.value)

        assertEquals(UpdateStatus.Ready("0.176.0"), foreground())
        assertEquals(bytes.toList(), File(updates, "176000.apk").readBytes().toList())
    }

    /**
     * The invariant an install rests on: the night lives only while its screen shows, so going to the
     * background stops it, and the install on the same transition proceeds only once that stop has
     * finished. (The screen stopping the night on ON_STOP is `TvGamesFrameTest.goingToTheBackgroundEndsTheNightAndLeaves`.)
     */
    @Test
    fun goingToTheBackgroundStopsTheNightBeforeTheInstallProceeds() {
        announcing()
        model.start()
        assertEquals(UpdateStatus.Ready("0.176.0"), foreground())
        val stopGate = CompletableDeferred<Unit>()
        core.stopGate = stopGate

        model.stop()
        updater.onBackground()
        assertEquals(1, core.stops, "the stop has begun")
        assertEquals(emptyList(), installed, "and the install waits for it")

        stopGate.complete(Unit)
        assertEquals(1, installed.size)
        assertEquals(listOf(true), installedAfterStop, "the stop had finished when the install went ahead")
    }

    @Test
    fun comingBackWhileTheStopIsUnderWayLeavesTheInstallForTheNextBackground() {
        announcing()
        model.start()
        assertEquals(UpdateStatus.Ready("0.176.0"), foreground())
        val stopGate = CompletableDeferred<Unit>()
        core.stopGate = stopGate
        model.stop()
        updater.onBackground()

        // The person is back before the stop has finished: the install must not end the night they return to.
        updater.onForeground()
        stopGate.complete(Unit)
        assertEquals(emptyList(), installed)
        assertEquals(UpdateStatus.Ready("0.176.0"), updater.status.value)

        updater.onBackground()
        assertEquals(1, installed.size)
    }

    @Test
    fun noMediaAppFailsTheCheckWithoutCrashingAndTheNextForegroundAsksAgain() {
        val failed = foreground()
        assertEquals(UpdateStatus.Failed("the media app did not answer"), failed)

        val media = announcing()
        assertEquals(UpdateStatus.Ready("0.176.0"), foreground { it is UpdateStatus.Ready })
        assertEquals("/release", media.queried.first())
    }
}

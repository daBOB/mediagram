package update

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** When the updater looks, downloads and installs. */
internal class AppUpdaterTest : UpdaterTestBase() {
    @Test
    fun aNewerReleaseIsDownloadedAndInstalledWhenTheAppLeaves() =
        runTest {
            val updater = updater()
            updater.checkAndDownload()
            assertEquals(UpdateStatus.Ready("0.93.0"), updater.status.value)
            assertEquals(listOf(File(dir, "93000.apk").path), source.downloadedPaths)
            updater.installIfReady()
            assertEquals(listOf(File(dir, "93000.apk")), installer.installed)
        }

    @Test
    fun nothingInstallsWhileSomethingPlays() =
        runTest {
            val updater = updater()
            updater.checkAndDownload()
            playing = true
            updater.installIfReady()
            assertTrue(installer.installed.isEmpty())
        }

    @Test
    fun nothingIsAskedWhileSomethingPlays() =
        runTest {
            playing = true
            val updater = updater()
            updater.checkAndDownload()
            assertEquals(0, source.asked)
            assertTrue(source.downloadedPaths.isEmpty())
        }

    @Test
    fun aReleaseNotNewerLeavesTheAppUpToDateAndClearsOldFiles() =
        runTest {
            File(dir, "92500.apk").writeText("old")
            source.latest = Lookup.Found(source.release(92_002L))
            val updater = updater()
            updater.checkAndDownload()
            assertEquals(UpdateStatus.UpToDate(1_000_000L), updater.status.value)
            assertTrue(dir.list().orEmpty().isEmpty())
            assertTrue(source.downloadedPaths.isEmpty())
        }

    @Test
    fun anAlreadyDownloadedReleaseIsNotFetchedAgainAndOlderOnesGo() =
        runTest {
            File(dir, "93000.apk").writeBytes(byteArrayOf(1, 2, 3))
            File(dir, "92500.apk").writeText("old")
            val updater = updater()
            updater.checkAndDownload()
            assertTrue(source.downloadedPaths.isEmpty())
            assertEquals(listOf("93000.apk"), dir.list().orEmpty().toList())
            assertEquals(UpdateStatus.Ready("0.93.0"), updater.status.value)
        }

    /** The row says what the source said: for a signed-out device, that it was signed out. */

    @Test
    fun aDownloadInFlightIsCancelledWhenPlaybackStartsAndRetriedLater() =
        runTest {
            source.downloadGate = CompletableDeferred()
            val updater = updater()
            val check = launch { updater.checkAndDownload() }
            advanceTimeBy(1_000)
            playing = true
            advanceTimeBy(2_001)
            runCurrent()
            assertTrue(check.isCompleted)
            assertEquals(UpdateStatus.NotChecked, updater.status.value)
            assertTrue(!File(dir, "93000.apk").exists())
            playing = false
            updater.installIfReady()
            assertTrue(installer.installed.isEmpty())

            source.downloadGate = null
            updater.checkAndDownload()
            assertEquals(2, source.downloadedPaths.size)
            assertEquals(UpdateStatus.Ready("0.93.0"), updater.status.value)
        }

    @Test
    fun aNewerReleaseThatFailsToDownloadDoesNotInstallTheDeletedOne() =
        runTest {
            val updater = updater()
            updater.checkAndDownload()
            assertEquals(UpdateStatus.Ready("0.93.0"), updater.status.value)
            source.latest = Lookup.Found(source.release(94_000L, "0.94.0"))
            source.downloadFailure = IllegalStateException("no network")
            updater.now = { 5_000_000L }
            updater.checkAndDownload()
            assertEquals(UpdateStatus.Failed("the update check did not finish"), updater.status.value)
            updater.installIfReady()
            assertTrue(installer.installed.isEmpty())
        }
}

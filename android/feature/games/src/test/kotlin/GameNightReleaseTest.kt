package games

import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import testing.FakeCoreProvider
import uniffi.mediagram_core.AppRelease
import uniffi.mediagram_core.CoreException
import uniffi.mediagram_core.ReleaseApp
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** What Spieltisch reads to update itself: its own newest release, and that release's APK. */
@RunWith(RobolectricTestRunner::class)
class GameNightReleaseTest {
    @get:Rule val folder = TemporaryFolder()

    private val app = RuntimeEnvironment.getApplication()
    private val f by lazy { NightFixture(folder.root) }
    private val release = AppRelease("0.176.0", 176_000L, 3uL, "b".repeat(64), -100L, 9L, null)

    @Before
    fun spieltischHoldsThePermission() = shadowOf(app).grantPermissions(GameNightProvider.PERMISSION)

    private fun pinned() {
        f.core.latestRelease = release
        f.core.releaseApk = byteArrayOf(1, 2, 3)
    }

    /** The release row's columns; an empty list for an empty answer (nothing pinned), `null` for no cursor at all (the lookup failed). */
    private fun row(): List<String>? =
        f.provider().query(f.uri("release"), null, null, null, null)?.use {
            if (!it.moveToFirst()) return emptyList()
            listOf(GameNightProvider.VERSION_NAME, GameNightProvider.VERSION_CODE, GameNightProvider.BYTES, GameNightProvider.SHA256)
                .map { column -> it.getString(it.getColumnIndexOrThrow(column)) }
        }

    @Test
    fun aPinnedSpieltischReleaseIsOneRow() {
        pinned()

        assertEquals(listOf("0.176.0", "176000", "3", "b".repeat(64)), row())
        assertEquals(ReleaseApp.SPIELTISCH, f.core.releaseApp, "the media app's own release is never what this path answers")
    }

    @Test
    fun noLibraryNoReleaseOrNoCoreIsAnEmptyAnswer() {
        pinned()
        runBlocking { f.library.clear() }
        assertEquals(emptyList(), row())
        runBlocking { f.library.write("library") }

        f.core.latestRelease = null
        assertEquals(emptyList(), row())

        pinned()
        f.coreProvider = FakeCoreProvider(null)
        assertEquals(emptyList(), row())
    }

    @Test
    fun aLookupThatFailsIsNoCursorSoItIsNeverTakenForNothingPinned() {
        pinned()
        f.core.releaseFailure = CoreException.NotAuthorized("signed out")
        assertEquals(null, row())

        f.core.releaseFailure = IllegalStateException("no core")
        assertEquals(null, row())

        f.core.releaseFailure = null
        f.coreProvider = FakeCoreProvider(f.core).apply { beforeCore = { error("the core could not be built") } }
        assertEquals(null, row())
    }

    @Test
    fun theApkIsTheCoresVerifiedDownloadInThisAppsCache() {
        pinned()

        val served = ParcelFileDescriptor.AutoCloseInputStream(f.provider().openFile(f.uri("release.apk"), "r")).use { it.readBytes() }
        assertEquals(listOf<Byte>(1, 2, 3), served.toList())
        val downloaded = File(f.core.downloadedPaths.single())
        assertEquals("176000.apk", downloaded.name)
        assertTrue(downloaded.startsWith(app.cacheDir), "downloaded into this app's cache, which only it can write")
    }

    @Test
    fun aDownloadAlreadyHereIsServedWithoutFetchingAgainAndOlderOnesGo() {
        pinned()
        val provider = f.provider()
        provider.openFile(f.uri("release.apk"), "r").close()
        val older = File(File(f.core.downloadedPaths.single()).parentFile, "175000.apk").apply { writeText("old") }

        provider.openFile(f.uri("release.apk"), "r").close()
        assertEquals(1, f.core.downloadedPaths.size)

        f.core.latestRelease = release.copy(versionCode = 177_000L, versionName = "0.177.0")
        provider.openFile(f.uri("release.apk"), "r").close()
        assertEquals(2, f.core.downloadedPaths.size)
        assertTrue(!older.exists() && !File(f.core.downloadedPaths.first()).exists(), "only the newest is kept")
    }

    @Test
    fun noApkWhenTheDownloadFailsOrThereIsNothingToDownload() {
        pinned()
        f.core.downloadFailure = CoreException.Network("offline")
        f.refused("release.apk")

        f.core.downloadFailure = null
        f.core.latestRelease = null
        f.refused("release.apk")
        assertEquals(1, f.core.downloadedPaths.size)
    }

    @Test
    fun aCallerThatCancelsEndsTheWaitForTheDownload() {
        pinned()
        f.core.downloadGate = CompletableDeferred()
        val signal = CancellationSignal()
        val outcome = mutableListOf<Throwable?>()
        val done = CountDownLatch(1)
        val provider = f.provider()
        thread(isDaemon = true) {
            outcome += runCatching { provider.openFile(f.uri("release.apk"), "r", signal) }.exceptionOrNull()
            done.countDown()
        }
        while (f.core.downloadedPaths.isEmpty()) Thread.sleep(10)

        signal.cancel()

        assertTrue(done.await(5, TimeUnit.SECONDS), "the provider call returned instead of waiting out the download")
        assertTrue(assertNotNull(outcome.single()) is OperationCanceledException)
    }

    @Test
    fun theReleaseIsReadOnlyAndNeedsThePermission() {
        pinned()
        val provider = f.provider()
        assertFailsWith<FileNotFoundException> { provider.openFile(f.uri("release.apk"), "rw") }

        shadowOf(app).denyPermissions(GameNightProvider.PERMISSION)
        assertFailsWith<SecurityException> { provider.query(f.uri("release"), null, null, null, null) }
        assertFailsWith<SecurityException> { provider.openFile(f.uri("release.apk"), "r") }
        assertEquals(0, f.core.releaseChecks)
    }
}

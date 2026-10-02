package update

import android.content.pm.PackageInstaller
import data.CoreProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import settings.InMemoryLibrarySettings
import testing.FakeCore
import testing.ResolvedCoreProvider
import uniffi.mediagram_core.AppRelease
import uniffi.mediagram_core.CoreInterface
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppUpdaterTest {
    private val dir: File = Files.createTempDirectory("updates").toFile()
    private val release = AppRelease("0.93.0", 93_000L, 3uL, "a".repeat(64), -100L, 7L)
    private val core = FakeCore().apply { latestRelease = release; releaseApk = byteArrayOf(1, 2, 3) }
    private var playing = false
    private val installer = RecordingInstaller()

    private suspend fun updater(
        handle: String? = "library",
        enabled: Boolean = true,
        provider: CoreProvider = ResolvedCoreProvider(core),
    ): AppUpdater {
        val settings = InMemoryLibrarySettings().apply { handle?.let { write(it) } }
        return AppUpdater(
            UpdateConfig(enabled = enabled, installedVersionCode = 92_002, updatesDir = dir),
            provider,
            settings,
            { playing },
            installer,
            CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        ).apply { now = { 1_000_000L } }
    }

    @Test
    fun aNewerReleaseIsDownloadedAndInstalledWhenTheAppLeaves() =
        runTest {
            val updater = updater()
            updater.checkAndDownload()
            assertEquals(UpdateStatus.Ready("0.93.0"), updater.status.value)
            assertEquals(listOf(File(dir, "93000.apk").path), core.downloadedPaths)
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
    fun aReleaseNotNewerLeavesTheAppUpToDateAndClearsOldFiles() =
        runTest {
            File(dir, "92500.apk").writeText("old")
            core.latestRelease = release.copy(versionCode = 92_002L)
            val updater = updater()
            updater.checkAndDownload()
            assertEquals(UpdateStatus.UpToDate(1_000_000L), updater.status.value)
            assertTrue(dir.list().orEmpty().isEmpty())
            assertTrue(core.downloadedPaths.isEmpty())
        }

    @Test
    fun anAlreadyDownloadedReleaseIsNotFetchedAgainAndOlderOnesGo() =
        runTest {
            File(dir, "93000.apk").writeBytes(byteArrayOf(1, 2, 3))
            File(dir, "92500.apk").writeText("old")
            val updater = updater()
            updater.checkAndDownload()
            assertTrue(core.downloadedPaths.isEmpty())
            assertEquals(listOf("93000.apk"), dir.list().orEmpty().toList())
            assertEquals(UpdateStatus.Ready("0.93.0"), updater.status.value)
        }

    @Test
    fun aFailedDownloadSaysWhyAndInstallsNothing() =
        runTest {
            core.downloadFailure = IllegalStateException("no network")
            val updater = updater()
            updater.checkAndDownload()
            assertEquals(UpdateStatus.Failed("no network"), updater.status.value)
            updater.installIfReady()
            assertTrue(installer.installed.isEmpty())
        }

    @Test
    fun aRefusedApkIsDeletedAndNotInstalled() =
        runTest {
            installer.refusal = "the download is signed with another key"
            val updater = updater()
            updater.checkAndDownload()
            updater.installIfReady()
            assertTrue(installer.installed.isEmpty())
            assertEquals(UpdateStatus.Failed("the download is signed with another key"), updater.status.value)
            assertTrue(!File(dir, "93000.apk").exists())
        }

    @Test
    fun noLibraryChosenOrADisabledBuildNeverAsksTheCore() =
        runTest {
            updater(handle = null).checkAndDownload()
            updater(enabled = false).checkAndDownload()
            assertEquals(0, core.releaseChecks)
        }

    @Test
    fun aCoreThatCannotBeBuiltFailsTheCheckInsteadOfCrashing() =
        runTest {
            val broken =
                object : CoreProvider by ResolvedCoreProvider(core) {
                    override suspend fun coreOrNull(): CoreInterface? = throw IllegalStateException("no core")
                }
            val updater = updater(provider = broken)
            updater.checkAndDownload()
            assertEquals(UpdateStatus.Failed("no core"), updater.status.value)
        }

    @Test
    fun anInstallThatThrowsDropsTheApkAndIsNotRetried() =
        runTest {
            installer.installFailure = IllegalStateException("session failed")
            val updater = updater()
            updater.checkAndDownload()
            updater.installIfReady()
            assertEquals(UpdateStatus.Failed("session failed"), updater.status.value)
            assertTrue(!File(dir, "93000.apk").exists())
            updater.installIfReady()
            assertEquals(1, installer.attempts)
        }

    @Test
    fun anInstallAndroidRefusesDropsTheApk() =
        runTest {
            val updater = updater()
            updater.checkAndDownload()
            updater.onInstallResult(PackageInstaller.STATUS_FAILURE, "storage", null)
            assertEquals(UpdateStatus.Failed("storage"), updater.status.value)
            assertTrue(!File(dir, "93000.apk").exists())
            updater.installIfReady()
            assertTrue(installer.installed.isEmpty())
        }

    @Test
    fun aDownloadInFlightIsCancelledWhenPlaybackStartsAndRetriedLater() =
        runTest {
            core.downloadGate = CompletableDeferred()
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

            core.downloadGate = null
            updater.checkAndDownload()
            assertEquals(2, core.downloadedPaths.size)
            assertEquals(UpdateStatus.Ready("0.93.0"), updater.status.value)
        }

    @Test
    fun aNewerReleaseThatFailsToDownloadDoesNotInstallTheDeletedOne() =
        runTest {
            val updater = updater()
            updater.checkAndDownload()
            assertEquals(UpdateStatus.Ready("0.93.0"), updater.status.value)
            core.latestRelease = release.copy(versionName = "0.94.0", versionCode = 94_000L)
            core.downloadFailure = IllegalStateException("no network")
            updater.now = { 5_000_000L }
            updater.checkAndDownload()
            assertEquals(UpdateStatus.Failed("no network"), updater.status.value)
            updater.installIfReady()
            assertTrue(installer.installed.isEmpty())
        }

    private class RecordingInstaller : ApkInstaller {
        var refusal: String? = null
        val installed = mutableListOf<File>()
        var installFailure: Exception? = null
        var attempts = 0

        override fun refusal(apk: File): String? = refusal

        override fun install(apk: File) {
            attempts++
            installFailure?.let { throw it }
            installed += apk
        }
    }
}

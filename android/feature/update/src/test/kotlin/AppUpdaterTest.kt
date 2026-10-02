package update

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import settings.InMemoryLibrarySettings
import testing.FakeCore
import testing.ResolvedCoreProvider
import uniffi.mediagram_core.AppRelease
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
    ): AppUpdater {
        val settings = InMemoryLibrarySettings().apply { handle?.let { write(it) } }
        return AppUpdater(
            UpdateConfig(enabled = enabled, installedVersionCode = 92_002, updatesDir = dir),
            ResolvedCoreProvider(core),
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

    private class RecordingInstaller : ApkInstaller {
        var refusal: String? = null
        val installed = mutableListOf<File>()

        override fun refusal(apk: File): String? = refusal

        override fun install(apk: File) {
            installed += apk
        }
    }
}

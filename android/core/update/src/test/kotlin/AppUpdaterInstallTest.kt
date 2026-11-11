package update

import android.content.pm.PackageInstaller
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** What the updater does with an APK the installer or Android refuses. */
internal class AppUpdaterInstallTest : UpdaterTestBase() {
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
    fun anInstallThatThrowsDropsTheApkAndIsNotRetried() =
        runTest {
            installer.installFailure = IllegalStateException("session failed")
            val updater = updater()
            updater.checkAndDownload()
            updater.installIfReady()
            assertEquals(UpdateStatus.Failed("the installer did not take the download"), updater.status.value)
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
    fun anInstallRefusedWithItsProfileIsRetriedWithoutIt() =
        runTest {
            val updater = updater()
            updater.checkAndDownload()
            File(dir, "93000.dm").writeText("profile")
            updater.onInstallResult(PackageInstaller.STATUS_FAILURE, "bad dex metadata", null)
            assertEquals(UpdateStatus.Ready("0.93.0"), updater.status.value)
            assertTrue(!File(dir, "93000.dm").exists())
            updater.installIfReady()
            assertEquals(listOf(File(dir, "93000.apk")), installer.installed)
        }

    @Test
    fun aRefusalReachingARestartedProcessStillDropsTheProfile() =
        runTest {
            File(dir, "93000.apk").writeText("apk")
            File(dir, "93000.dm").writeText("profile")
            val updater = updater()
            updater.onInstallResult(PackageInstaller.STATUS_FAILURE, "bad dex metadata", null)
            assertTrue(!File(dir, "93000.dm").exists())
            assertTrue(File(dir, "93000.apk").exists(), "the APK stays for the retry")
        }

    @Test
    fun anInstallIsSkippedWhenTheAppIsBackInFrontAndGoesAheadAtTheNextBackground() =
        runTest {
            File(dir, "93000.apk").writeBytes(byteArrayOf(1, 2, 3))
            val updater = updater()
            updater.onForeground()
            assertEquals(UpdateStatus.Ready("0.93.0"), updater.status.value)

            updater.installIfReady()
            assertTrue(installer.installed.isEmpty(), "the person is in the app: an install would end what they came back to")
            assertEquals(UpdateStatus.Ready("0.93.0"), updater.status.value)
            assertTrue(File(dir, "93000.apk").exists(), "and the download stays for later")

            updater.onBackground()
            assertEquals(listOf(File(dir, "93000.apk")), installer.installed)
        }
}

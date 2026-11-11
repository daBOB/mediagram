package update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** What the updater makes of a source that fails or cannot be asked. */
internal class AppUpdaterSourceTest : UpdaterTestBase() {
    @Test
    fun aFailedDownloadSaysWhyAndInstallsNothing() =
        runTest {
            source.downloadFailure = SignedOut()
            val updater = updater()
            updater.checkAndDownload()
            assertEquals(UpdateStatus.Failed("this device was signed out of Telegram"), updater.status.value)
            updater.installIfReady()
            assertTrue(installer.installed.isEmpty())
        }

    @Test
    fun aSourceThatCannotBeAskedYetIsAskedAgainAtTheNextForeground() =
        runTest {
            source.latest = Lookup.Unavailable()
            val updater = updater()
            updater.checkAndDownload()
            updater.checkAndDownload()
            assertEquals(2, source.asked, "no look at the channel happened, so nothing throttles the next")
            assertEquals(UpdateStatus.NotChecked, updater.status.value)
        }

    @Test
    fun aSourceThatSaysWhyItCannotBeAskedShowsItAndIsAskedAgain() =
        runTest {
            source.latest = Lookup.Unavailable("the media app did not answer")
            val updater = updater()
            updater.checkAndDownload()
            assertEquals(UpdateStatus.Failed("the media app did not answer"), updater.status.value)
            updater.checkAndDownload()
            assertEquals(2, source.asked)
        }

    @Test
    fun aDisabledBuildNeverAsksTheSource() =
        runTest {
            updater(enabled = false).checkAndDownload()
            assertEquals(0, source.asked)
        }

    @Test
    fun aSourceThatFailsFailsTheCheckInsteadOfCrashing() =
        runTest {
            source.lookupFailure = IllegalStateException("no core")
            val updater = updater()
            updater.checkAndDownload()
            assertEquals(UpdateStatus.Failed("the update check did not finish"), updater.status.value)
        }

    @Test
    fun aLookupThatFailsKeepsTheHourlyThrottle() =
        runTest {
            source.lookupFailure = IllegalStateException("offline")
            val updater = updater()
            updater.checkAndDownload()
            updater.checkAndDownload()
            assertEquals(1, source.asked, "asked the channel and failed: the next look is an hour away")
        }
}

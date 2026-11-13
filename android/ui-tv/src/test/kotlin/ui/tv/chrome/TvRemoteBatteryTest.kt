package ui.tv.chrome

import android.content.Intent
import android.os.Looper
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import catalog.ChromeCounts
import designsystem.Overscan
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import ui.common.RailItem
import ui.tv.catalog.TvScreenStateTest

/** Funstation's host tells the app the remote's battery; the rail shows it at its foot, and nothing anywhere else. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-xhdpi")
class TvRemoteBatteryTest : TvScreenStateTest() {
    private val rows = RailItem.entries.associateWith { FocusRequester() }

    @Test
    fun aRailNobodyToldShowsNoRemote() {
        showRail()
        compose.onNodeWithContentDescription("Remote battery", substring = true).assertDoesNotExist()
    }

    @Test
    fun theReportedBatterySitsAtTheRailsFootAndOutlastsTheApp() {
        showRail()
        report(73)
        val badge = compose.onNodeWithContentDescription("Remote battery 73%").getUnclippedBoundsInRoot()
        val rail = compose.onNodeWithTag(RAIL).getUnclippedBoundsInRoot()
        assertEquals((rail.bottom - Overscan.vertical).value, badge.bottom.value, 1f, "at the rail's foot, on the overscan line")

        report(140)
        compose.onNodeWithContentDescription("Remote battery 73%").assertExists()

        close()
        showRail()
        compose.onNodeWithContentDescription("Remote battery 73%").assertExists()
    }

    private fun showRail() =
        show {
            Row {
                TvLibraryRail(
                    active = null,
                    counts = ChromeCounts(myList = 0, continueWatching = 0, perShelf = emptyMap(), collections = 0),
                    tally = emptyList(),
                    rowRequesters = rows,
                    onSelect = {},
                    onRight = {},
                    onHasFocusChanged = {},
                    modifier = Modifier.testTag(RAIL),
                )
            }
        }

    private fun report(percent: Int) {
        val intent = Intent(REMOTE_BATTERY_ACTION).putExtra(REMOTE_BATTERY_PERCENT, percent)
        compose.runOnUiThread {
            RuntimeEnvironment.getApplication().sendBroadcast(intent, REMOTE_BATTERY_SENDER)
            shadowOf(Looper.getMainLooper()).idle()
        }
        compose.waitForIdle()
    }

    private companion object {
        const val RAIL = "rail"
    }
}

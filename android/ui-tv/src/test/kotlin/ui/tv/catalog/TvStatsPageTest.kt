package ui.tv.catalog

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import stats.StatsBar
import stats.StatsLine
import stats.StatsUiState

/** [TvStatsPage] with real D-pad keys, in the harness every catalogue page's own test uses. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvStatsPageTest : TvScreenStateTest() {
    private fun line(n: Int) = "Started · Film $n · today 21:14 · 42 min"

    private fun ready(lines: Int) =
        StatsUiState.Ready(
            totals = listOf("This week" to "42 min", "This month" to "3 h 12 min", "All time" to "12 h"),
            bars = List(30) { StatsBar(fraction = if (it == 29) 1f else 0f, initial = "S", description = "day $it") },
            history = List(lines) { StatsLine(key = "STARTED:f$it", text = line(it)) },
        )

    @Test
    fun nothingWatchedSaysSoUnderTheHeading() {
        show { TvStatsPage(StatsUiState.Empty) }
        compose.onNodeWithText("Stats").assertExists()
        compose.onNodeWithText("Nothing watched yet.").assertExists()
        compose.onNodeWithText("This week").assertDoesNotExist()
        compose.onNodeWithText("History").assertDoesNotExist()
    }

    @Test
    fun aFailedReadSaysSo() {
        show { TvStatsPage(StatsUiState.Failed("Could not read your stats: database is locked")) }
        compose.onNodeWithText("Could not read your stats: database is locked").assertExists()
        compose.onNodeWithText("Nothing watched yet.").assertDoesNotExist()
    }

    @Test
    fun theTotalsTakeTheRemoteOnArrival() {
        show { TvStatsPage(ready(lines = 3)) }
        compose.onNode(hasText("This week")).assertIsFocused()
        compose.onNodeWithText("3 h 12 min").assertExists()
        compose.onNodeWithContentDescription("day 29").assertExists()
    }

    @Test
    fun aLongHistoryIsComposedLazilyAndTheRemoteWalksDownIt() {
        show { TvStatsPage(ready(lines = 500)) }
        compose.onNodeWithText(line(499)).assertDoesNotExist()

        // Totals, then the chart, then one press per history line.
        repeat(12) { compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) } }

        compose.onNodeWithText(line(10)).assertIsFocused().assertIsDisplayed()
    }
}

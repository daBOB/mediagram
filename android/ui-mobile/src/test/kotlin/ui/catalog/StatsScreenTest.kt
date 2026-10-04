package ui.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import stats.StatsBar
import stats.StatsLine
import stats.StatsUiState
import kotlin.test.assertTrue

/** [StatsScreen] rendered straight from a state; the strings themselves are `feature:stats`' and tested there. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StatsScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(state: StatsUiState) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { MaterialTheme { StatsScreen(state) } }
        }
        compose.waitForIdle()
    }

    private val ready =
        StatsUiState.Ready(
            totals = listOf("This week" to "42 min", "This month" to "3 h 12 min", "All time" to "12 h"),
            bars = List(30) { StatsBar(fraction = if (it == 29) 1f else 0f, initial = listOf("M", "T", "W", "T", "F", "S", "S")[it % 7], description = "day $it") },
            history =
                listOf(
                    StatsLine("STARTED:f1", "Started · Der Pate · today 21:14 · 42 min"),
                    StatsLine("FINISHED:gone", "Finished · No longer in the library · 19 Sep"),
                ),
        )

    @Test
    fun nothingWatchedSaysSoUnderTheHeading() {
        show(StatsUiState.Empty)
        compose.onNodeWithText("Stats").assertIsDisplayed()
        compose.onNodeWithText("Nothing watched yet.").assertIsDisplayed()
        compose.onNodeWithText("This week").assertDoesNotExist()
        compose.onNodeWithText("History").assertDoesNotExist()
        compose.onNodeWithText("Counting since", substring = true).assertDoesNotExist()
    }

    @Test
    fun aReadyPageShowsTheTotalsTheChartAndEveryLine() {
        show(ready)
        compose.onNodeWithText("This month").assertIsDisplayed()
        compose.onNodeWithText("3 h 12 min").assertIsDisplayed()
        compose.onNodeWithText("Last 30 days").assertIsDisplayed()
        compose.onNodeWithContentDescription("day 29").assertExists()
        compose.onNodeWithText("Finished · No longer in the library · 19 Sep").assertExists()
        compose.onNodeWithText("Counting since", substring = true).assertDoesNotExist()
    }

    @Test
    fun whileNothingIsCountedTheLineSitsBetweenTheTotalsAndTheChart() {
        show(ready.copy(totals = ready.totals.map { (label, _) -> label to "0 min" }, countingSince = "Counting since 3 Oct"))
        compose.onNodeWithText("Counting since 3 Oct").assertIsDisplayed()
        val top = { text: String -> compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top }
        assertTrue(top("This week") < top("Counting since 3 Oct"), "under the totals")
        assertTrue(top("Counting since 3 Oct") < top("Last 30 days"), "above the chart")
    }

    @Test
    fun aCompactScreenLeavesTheWeekdayLettersOut() {
        show(ready)
        compose.onAllNodesWithText("M").assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "w1164dp-h777dp")
    fun anExpandedScreenLettersEachDay() {
        show(ready)
        compose.onAllNodesWithText("M").assertCountEquals(5)
    }
}

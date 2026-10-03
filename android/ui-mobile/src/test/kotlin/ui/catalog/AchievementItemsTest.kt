package ui.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import stats.AchievementsUi
import stats.EarnedLine
import stats.NextLine
import stats.StatsBar
import stats.StatsLine
import stats.StatsUiState

/** The Achievements section on [StatsScreen], drawn straight from a state; its words are `feature:stats`' and tested there. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AchievementItemsTest {
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
            totals = listOf("This week" to "42 min", "This month" to "42 min", "All time" to "42 min"),
            bars = List(30) { StatsBar(fraction = 0f, initial = "M", description = "day $it") },
            history = listOf(StatsLine("STARTED:f1", "Started · Der Pate · today 21:14 · 42 min")),
        )

    private fun seen(text: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    @Test
    fun earnedAchievementsShowTheirDayAndTheNextHowFarAlong() {
        show(
            ready.copy(
                achievements =
                    AchievementsUi(
                        earned = listOf(EarnedLine(id = "films-1", label = "First film", on = "today")),
                        next = listOf(NextLine(id = "films-10", label = "10 films", progress = "1 of 10 films", fraction = 0.1f)),
                    ),
            ),
        )
        for (text in listOf("Achievements", "First film", "today", "Next", "10 films", "1 of 10 films", "History")) seen(text)
    }

    @Test
    fun nothingEarnedAndNothingToComeDrawsNoSection() {
        show(ready)
        compose.onNodeWithText("Achievements").assertDoesNotExist()
    }
}

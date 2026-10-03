package ui.tv.catalog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import stats.AchievementsUi
import stats.EarnedLine
import stats.NextLine
import stats.StatsBar
import stats.StatsLine
import stats.StatsUiState

/** The Achievements section on [TvStatsPage], in the harness every catalogue page's own test uses. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvAchievementItemsTest : TvScreenStateTest() {
    private val ready =
        StatsUiState.Ready(
            totals = listOf("This week" to "42 min", "This month" to "42 min", "All time" to "42 min"),
            bars = List(30) { StatsBar(fraction = 0f, initial = "M", description = "day $it") },
            history = listOf(StatsLine("STARTED:f1", "Started · Der Pate · today 21:14 · 42 min")),
        )

    @Test
    fun earnedAchievementsShowTheirDayAndTheNextHowFarAlong() {
        show {
            TvStatsPage(
                ready.copy(
                    achievements =
                        AchievementsUi(
                            earned = listOf(EarnedLine(id = "films-1", label = "First film", on = "today")),
                            next = listOf(NextLine(id = "films-10", label = "10 films", progress = "1 of 10 films", fraction = 0.1f)),
                        ),
                ),
            )
        }
        for (text in listOf("Achievements", "First film", "today", "Next", "1 of 10 films", "History")) {
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
            compose.onNodeWithText(text).assertIsDisplayed()
        }
    }

    @Test
    fun nothingEarnedAndNothingToComeDrawsNoSection() {
        show { TvStatsPage(ready) }
        compose.onNodeWithText("Achievements").assertDoesNotExist()
    }
}

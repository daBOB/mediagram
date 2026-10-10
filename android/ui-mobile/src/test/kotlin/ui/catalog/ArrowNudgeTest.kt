package ui.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import designsystem.MediagramTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.catalog.home.BandHeading
import kotlin.test.assertEquals

/**
 * An arrow nudges toward where it leads while pressed — the touch form of
 * the web's hover — and nothing else in its control moves. The glyph's
 * layer translation shows in its bounds in root; the press's 0.98 snap is
 * drawn, not laid out, so it doesn't move them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArrowNudgeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { MediagramTheme { content() } }
        }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
    }

    private fun left(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot().left.value

    private fun press(text: String) = compose.onNodeWithText(text).performTouchInput { down(center) }

    private fun release(text: String) = compose.onNodeWithText(text).performTouchInput { up() }

    @Test fun thePlayTriangleNudgesWhilePressedAndOnlyItMoves() {
        show {
            TitlePills(
                playLabel = "Play", onPlay = {}, watchlisted = false, onToggleWatchlist = {},
                editorsChoicePinned = false, onToggleEditorsChoice = null,
            )
        }
        compose.onNodeWithText("▶ Play").assertExists()
        val glyph = left("▶")
        val label = left("Play")
        press("▶ Play")
        compose.mainClock.advanceTimeBy(600)
        assertEquals(glyph + 2f, left("▶"), 0.3f, "the triangle nudges 2dp toward the film")
        assertEquals(label, left("Play"), 0.3f, "and the label stays put")
        release("▶ Play")
        compose.mainClock.advanceTimeBy(600)
        assertEquals(glyph, left("▶"), 0.3f)
    }

    @Test fun seeAllsArrowNudgesWhilePressedAndTheLinkStillReadsAsBefore() {
        var opened = 0
        show { BandHeading(title = "Continue Watching", count = null, onSeeAll = { opened++ }) }
        val arrow = left("→")
        press("See all →")
        compose.mainClock.advanceTimeBy(600)
        assertEquals(arrow + 4f, left("→"), 0.3f, "the arrow nudges 4dp toward the wall")
        release("See all →")
        compose.mainClock.advanceTimeBy(600)
        assertEquals(arrow, left("→"), 0.3f)
        assertEquals(1, opened)
    }

    @Test fun aDepartmentRowsLinkGainsTheWebsArrowAndStillReadsAsItsLabel() {
        show { DeptRowHeading(title = "Featured Movies", onSeeAll = {}, seeAllLabel = "All 3 films") }
        compose.onNodeWithText("All 3 films").assertExists()
        compose.onAllNodesWithText("All 3 films →").assertCountEquals(0)
        val arrow = left("→")
        press("All 3 films")
        compose.mainClock.advanceTimeBy(600)
        assertEquals(arrow + 4f, left("→"), 0.3f)
    }

    @Test fun theAllNPillMovesAsAWholeAndANewListPillStaysPut() {
        show {
            Column {
                PagePill(text = "All 3 films →", onClick = {}, large = true)
                PagePill(text = "＋ New list", onClick = {})
            }
        }
        val all = left("All 3 films →")
        press("All 3 films →")
        compose.mainClock.advanceTimeBy(600)
        assertEquals(all + 3f, left("All 3 films →"), 0.3f, "the whole pill moves toward the shelf")
        release("All 3 films →")
        compose.mainClock.advanceTimeBy(600)
        val make = left("＋ New list")
        press("＋ New list")
        compose.mainClock.advanceTimeBy(600)
        assertEquals(make, left("＋ New list"), 0.3f, "a button-type pill has no motion of its own")
    }

    @Test fun seeAllsTapTargetIncludesItsVerticalPadding() {
        show { BandHeading(title = "Continue Watching", count = null, onSeeAll = {}) }
        val link = compose.onNodeWithText("See all →").getUnclippedBoundsInRoot()
        val label = compose.onNodeWithText("See all", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals((label.bottom - label.top).value + 24f, (link.bottom - link.top).value, 0.5f, "the 12 dp above and below are part of the target")
    }
}

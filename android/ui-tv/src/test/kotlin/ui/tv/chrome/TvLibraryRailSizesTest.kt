package ui.tv.chrome

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import catalog.ChromeCounts
import designsystem.Overscan
import designsystem.TvScale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ui.common.RailItem
import ui.tv.catalog.TvScreenStateTest
import ui.tv.fontSizeOf

/**
 * The rail at the web's rail for the TV's 1280px reading (`shell.css:17-70`),
 * every px through [TvScale]: 14rem wide, 18 in from either side, each row 12
 * in again around a 21 icon, labels at 14px, counts and the tally at 10px.
 * Collapsed, it is the open rail's icon column and nothing else, so opening
 * it moves no row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvLibraryRailSizesTest : TvScreenStateTest() {
    private val rows = RailItem.entries.associateWith { FocusRequester() }
    private val elsewhere = FocusRequester()

    @Test
    fun theOpenRailIsTheWebsRail() {
        showRail()
        open()

        val wordmark = compose.onNodeWithText("mediagram").getUnclippedBoundsInRoot()
        assertEquals(224 * TvScale, railWidth(), OnePixel, "`--rail-width: 14rem`")
        assertEquals(14 * TvScale, compose.fontSizeOf(RailItem.CONTINUE_WATCHING.label).value, "`.rail-nav a`, 0.875rem")
        assertEquals(10 * TvScale, compose.fontSizeOf("12").value, "`.n`, 0.625rem")
        assertEquals(10 * TvScale, compose.fontSizeOf("1,143 FILMS").value, "`.rail-masthead`, 0.625rem")
        assertEquals(27.2f * TvScale, compose.fontSizeOf("mediagram").value, 0.001f, "`.brand`, 1.7rem")
        assertEquals((18 + 10) * TvScale, wordmark.left.value, OnePixel, "`.library-rail` 18 + `.brand` 10")
        assertTrue(wordmark.right.value <= (224 - 18) * TvScale, "the wordmark fits the open rail: $wordmark")
        assertEquals((18 + 12 + 21 + 12) * TvScale, labelLeft(RailItem.MY_LIST), OnePixel, "rail 18, row 12, icon 21, gap 12")
        assertEquals((44 + 2) * TvScale, gap(RailItem.MY_LIST, RailItem.CONTINUE_WATCHING), OnePixel, "44 a row, `.rail-nav` gap 2")
        assertEquals((44 + 2 + 12) * TvScale, gap(RailItem.SETTINGS, RailItem.SYSTEM), OnePixel, "`.apart` 12 on top of the gap")
        for (item in RailItem.entries) {
            assertFalse(layoutOf(item.label).isLineEllipsized(0), "${item.label} is cut short in the open rail")
        }
    }

    @Test
    fun collapsedIsTheOpenRailsIconColumnAndOpeningMovesNoRow() {
        showRail()

        val collapsed = collapsedRow(RailItem.MY_LIST)
        assertEquals((18 + 12 + 21 + 12 + 18) * TvScale, railWidth(), OnePixel, "18 + 12 + icon 21 + 12 + 18")
        assertEquals(18 * TvScale, collapsed.left.value, OnePixel, "`.library-rail` padding, not the 48dp overscan margin")
        assertEquals((12 + 21 + 12) * TvScale, collapsed.width.value, OnePixel, "`.rail-nav a` padding around the 21 icon")

        open()
        val opened = openRow(RailItem.MY_LIST)
        val wordmark = compose.onNodeWithText("mediagram").getUnclippedBoundsInRoot()
        assertEquals(Overscan.vertical.value, wordmark.top.value, OnePixel, "on the bar's top line, the screen's vertical overscan")
        assertEquals(wordmark.bottom.value + 36 * TvScale, opened.top.value, OnePixel, "`.brand`'s 36 below it")
        assertEquals(collapsed.left, opened.left, "the row keeps its start as the rail opens")
        assertEquals(collapsed.top, opened.top, "the rows land at the same height either way")
    }

    private fun showRail() {
        show {
            Row {
                TvLibraryRail(
                    active = null,
                    counts = ChromeCounts(myList = 12, continueWatching = 3, perShelf = emptyMap(), collections = 0),
                    tally = listOf("1,143 films", "171 shows"),
                    rowRequesters = rows,
                    onSelect = {},
                    onRight = {},
                    onHasFocusChanged = {},
                    modifier = Modifier.testTag(RAIL),
                )
                Box(Modifier.size(100.dp).focusRequester(elsewhere).focusable())
            }
            // A rail row is focusable only off touch mode, as on a remote.
            val inputMode = LocalInputModeManager.current
            LaunchedEffect(Unit) {
                inputMode.requestInputMode(InputMode.Keyboard)
                elsewhere.requestFocus()
            }
        }
        compose.waitForIdle()
    }

    private fun open() {
        compose.runOnUiThread { rows.getValue(RailItem.MY_LIST).requestFocus() }
        compose.waitForIdle()
    }

    private fun railWidth(): Float = compose.onNodeWithTag(RAIL).getUnclippedBoundsInRoot().width.value

    private fun collapsedRow(item: RailItem): DpRect = compose.onNodeWithContentDescription(item.label).getUnclippedBoundsInRoot()

    private fun openRow(item: RailItem): DpRect = compose.onNodeWithText(item.label, useUnmergedTree = true).onParent().getUnclippedBoundsInRoot()

    private fun gap(above: RailItem, below: RailItem): Float = openRow(below).top.value - openRow(above).top.value

    private fun labelLeft(item: RailItem): Float = compose.onNodeWithText(item.label, useUnmergedTree = true).getUnclippedBoundsInRoot().left.value

    private fun layoutOf(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.first()
    }

    private companion object {
        const val RAIL = "rail"

        /** Layout rounds to whole pixels; at the box's density one is half a dp. */
        const val OnePixel = 0.5f
    }
}

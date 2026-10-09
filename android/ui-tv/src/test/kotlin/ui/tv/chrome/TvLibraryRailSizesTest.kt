package ui.tv.chrome

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import catalog.ChromeCounts
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ui.common.RailItem
import ui.tv.catalog.TvScreenStateTest
import ui.tv.fontSizeOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rail opened by the remote, at the web's rail (`shell.css:17-70`):
 * 224 wide, labels at 14px, counts and the tally lifted from the web's 10px
 * to the television's 11sp floor, the wordmark at 1.7rem — and at that
 * width nothing is cut short.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvLibraryRailSizesTest : TvScreenStateTest() {
    @Test
    fun theOpenRailIsTheWebsRail() {
        val rows = RailItem.entries.associateWith { FocusRequester() }
        show {
            TvLibraryRail(
                active = null,
                counts = ChromeCounts(myList = 12, continueWatching = 3, perShelf = emptyMap(), collections = 0),
                tally = listOf("1,143 films", "171 shows"),
                rowRequesters = rows,
                onSelect = {},
                onRight = {},
                onHasFocusChanged = {},
            )
            // A rail row is focusable only off touch mode, as on a remote.
            val inputMode = LocalInputModeManager.current
            LaunchedEffect(Unit) {
                inputMode.requestInputMode(InputMode.Keyboard)
                rows.getValue(RailItem.MY_LIST).requestFocus()
            }
        }
        compose.waitForIdle()

        val wordmark = compose.onNodeWithText("mediagram").getUnclippedBoundsInRoot()
        assertEquals(224.dp, RailOpenWidth)
        assertEquals(14f, compose.fontSizeOf(RailItem.CONTINUE_WATCHING.label).value, "`.rail-nav a`, 0.875rem")
        assertEquals(11f, compose.fontSizeOf("12").value, "`.n`, the web's 10px at the floor")
        assertEquals(11f, compose.fontSizeOf("1,143 FILMS").value, "`.rail-masthead`, the web's 10px at the floor")
        assertEquals(27f, compose.fontSizeOf("mediagram").value, "`.brand`, 1.7rem")
        assertTrue(wordmark.right <= RailOpenWidth, "the wordmark fits the open rail: $wordmark")
        for (item in RailItem.entries) {
            assertFalse(layoutOf(item.label).isLineEllipsized(0), "${item.label} is cut short at 224dp")
        }
    }

    private fun layoutOf(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.first()
    }
}

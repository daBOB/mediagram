package ui.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import catalog.SearchFilter
import data.settings.ShelfView
import designsystem.MediagramTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.chrome.LibraryRail
import ui.common.RailItem
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * A chosen control's ink fades over `--hover` (160ms) rather than snapping.
 * Text colour is readable through the text layout a node reports, which
 * carries the colour it was composed with. Fills, borders and rings are
 * drawn and can't be read here (see PageGroundColorTest); the device walk
 * checks those.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SelectionColourFadeTest {
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

    private fun colourOf(text: String): Color {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first().layoutInput.style.color
    }

    /** Just after a choice: neither colour yet; once the fade is done, [to]. */
    private fun assertFades(text: String, from: Color, to: Color) {
        assertNotEquals(from, to, "the two states must differ for this check to mean anything")
        compose.mainClock.advanceTimeBy(64)
        val mid = colourOf(text)
        assertNotEquals(from, mid, "$text has started to fade")
        assertNotEquals(to, mid, "$text has not snapped")
        compose.mainClock.advanceTimeBy(250)
        assertEquals(to, colourOf(text))
    }

    @Test fun theShelfModeTogglesChosenInkFadesIn() {
        var current by mutableStateOf(ShelfView.LIST)
        var on = Color.Unspecified
        var off = Color.Unspecified
        show {
            on = MaterialTheme.colorScheme.onSurface
            off = MaterialTheme.colorScheme.onSurfaceVariant
            ShelfModeToggle(current = current, onChoose = { current = it })
        }
        assertEquals(off, colourOf("Grid"))
        compose.onNodeWithText("Grid").performClick()
        assertFades("Grid", from = off, to = on)
    }

    @Test fun aRailRowsInkFadesWhenItBecomesTheActiveOne() {
        var active by mutableStateOf<RailItem?>(RailItem.LATEST)
        var on = Color.Unspecified
        var off = Color.Unspecified
        show {
            on = MaterialTheme.colorScheme.onBackground
            off = MaterialTheme.colorScheme.onSurfaceVariant
            LibraryRail(active = active, onHome = {}, onSelect = { active = it })
        }
        assertEquals(off, colourOf("Genres"))
        compose.onNodeWithText("Genres").performClick()
        assertFades("Genres", from = off, to = on)
    }

    @Test fun aSearchFilterFadesItsInkWhenChosen() {
        var chosen by mutableStateOf(SearchFilter.ALL)
        var on = Color.Unspecified
        var off = Color.Unspecified
        show {
            on = MaterialTheme.colorScheme.onPrimaryContainer
            off = MaterialTheme.colorScheme.onSurfaceVariant
            SearchFilterChips(listOf(SearchFilter.ALL to 3, SearchFilter.MOVIES to 2), chosen) { chosen = it }
        }
        assertEquals(off, colourOf("Movies 2"))
        compose.onNodeWithText("Movies 2").performClick()
        assertFades("Movies 2", from = off, to = on)
    }
}

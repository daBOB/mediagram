package ui.tv.catalog

import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.tv.material3.MaterialTheme
import designsystem.Accent
import designsystem.TvStyle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.tv.FocusFillInk
import kotlin.test.assertEquals

/**
 * The title page's and the department pages' own pills and tabs say focus
 * the way the style does: a white fill with dark ink in Apple TV and
 * Google TV styles, mediagram's accent everywhere else.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvTitleFocusFillsStateTest : TvScreenStateTest() {
    @Test
    fun aFocusedSpreadPillTakesTheFillsDarkInkInAppleTvStyle() {
        show(TvStyle.APPLE) { TvSpreadPill(text = "+ My List", onClick = {}) }
        focus("+ My List")
        assertEquals(FocusFillInk, styleOf("+ My List").color)
    }

    @Test
    fun aFocusedSpreadPillKeepsItsOwnInkInMediagram() {
        var ink = Color.Unspecified
        var ground = Color.Unspecified
        show {
            ink = MaterialTheme.colorScheme.onSurface
            ground = MaterialTheme.colorScheme.background
            Row {
                TvSpreadPill(text = "+ My List", onClick = {})
                TvSpreadPill(text = "Play", onClick = {}, solid = true)
            }
        }
        focus("+ My List")
        assertEquals(ink, styleOf("+ My List").color)
        focus("Play")
        assertEquals(ground, styleOf("Play").color)
    }

    @Test
    fun aFocusedPagePillTakesTheFillsDarkInkOnlyWhereTheStyleFills() {
        show(TvStyle.GOOGLE) { TvPagePill(text = "All 3 films", onClick = {}) }
        focus("All 3 films")
        assertEquals(FocusFillInk, styleOf("All 3 films").color)
        close()

        var ink = Color.Unspecified
        show {
            ink = MaterialTheme.colorScheme.onSurface
            TvPagePill(text = "All 3 films", onClick = {})
        }
        focus("All 3 films")
        assertEquals(ink, styleOf("All 3 films").color)
    }

    @Test
    fun aFocusedTabIsDarkInkOnTheFillInAppleTvStyleAndTheAccentInMediagram() {
        show(TvStyle.APPLE) { TvSectionTabs(titles = listOf("Overview", "Cast"), selected = 0, onSelect = {}) }
        focus("Cast")
        assertEquals(FocusFillInk, styleOf("Cast").color)
        close()

        show { TvSectionTabs(titles = listOf("Overview", "Cast"), selected = 0, onSelect = {}) }
        focus("Cast")
        assertEquals(Accent.Default.dark, styleOf("Cast").color)
    }

    private fun focus(text: String) {
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
    }
}

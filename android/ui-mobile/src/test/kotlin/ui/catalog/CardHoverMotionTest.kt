package ui.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import data.PortraitRequestLog
import designsystem.MediagramTheme
import model.Credit
import model.TitleCredits
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Artwork moves for a hovering mouse or stylus and never for a finger, the
 * web's `(hover: hover)` gate (`catalog.css:51`). A layer's translation shows
 * in a node's bounds in root, so a plate's lift is read off the initials it
 * carries in place of a poster, and a face's off its initials. A zoom moves
 * only an image, which has no semantics node to measure; the module's own
 * tests and the device walk cover those. A name's colour is read from the
 * text layout it reports, which carries the colour it was composed with.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CardHoverMotionTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    private var accent = Color.Unspecified

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MediagramTheme {
                    accent = MaterialTheme.colorScheme.primary
                    Box { content() }
                }
            }
        }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
    }

    private fun top(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot().top.value

    private fun colourOf(text: String): Color {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first().layoutInput.style.color
    }

    private fun plate() = show { PosterCard(posterPath = null, title = "Dune", caption = null, modifier = Modifier.width(140.dp), onClick = {}) }

    @Test fun aPlateLiftsForAHoveringPointerButNeverForAFinger() {
        plate()
        val rest = top("D")
        compose.onNodeWithText("Dune").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(800)
        assertEquals(rest, top("D"), 0.3f, "a finger never lifts the plate")
        compose.onNodeWithText("Dune").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Dune").performMouseInput { enter(center) }
        compose.mainClock.advanceTimeBy(800)
        assertEquals(rest - 6f, top("D"), 0.3f, "a hovering pointer lifts it 6dp")
    }

    @Test fun thePlatesNameFadesToTheAccentWhilePressedAndBack() {
        plate()
        val rest = colourOf("Dune")
        assertNotEquals(accent, rest)
        compose.onNodeWithText("Dune").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(80)
        val mid = colourOf("Dune")
        assertNotEquals(rest, mid, "the name has started to fade")
        assertNotEquals(accent, mid, "rather than snapping")
        compose.mainClock.advanceTimeBy(250)
        assertEquals(accent, colourOf("Dune"))
        compose.onNodeWithText("Dune").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(250)
        assertEquals(rest, colourOf("Dune"))
    }

    @Test fun aCastFaceLiftsForAHoveringPointerButNeverForAFinger() {
        show {
            CastPanel(
                credits = TitleCredits(cast = listOf(Credit(personId = 1, name = "Ada Lovelace", role = "Herself", portraitPath = null)), crew = emptyList()),
                onOpenPerson = {},
                fetchPortrait = { null },
                portraits = PortraitRequestLog(),
            )
        }
        val rest = top("AL")
        compose.onNodeWithText("Ada Lovelace").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(600)
        assertEquals(rest, top("AL"), 0.3f, "a finger never lifts the face")
        compose.onNodeWithText("Ada Lovelace").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Ada Lovelace").performMouseInput { enter(center) }
        compose.mainClock.advanceTimeBy(600)
        assertEquals(rest - 3f, top("AL"), 0.3f, "a hovering pointer lifts it 3dp")
    }
}

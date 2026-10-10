package ui.player

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import designsystem.Appearance
import designsystem.IndicationTone
import designsystem.LocalIndicationTone
import designsystem.MediagramTheme
import designsystem.Palette
import designsystem.ThemeChoice
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.chrome.CatalogueMenu

/**
 * A press over the video in the light theme, where the page's ink is
 * near-black and so is the picture: the tint has to be white there, and the
 * page's ink again on what opens from it. Tints are drawn, and nothing here
 * captures pixels (PageGroundColorTest), so these read the tone the
 * indication draws in and the colour a tool's label is laid out in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerOverVideoInkTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { MediagramTheme(appearance = Appearance(theme = ThemeChoice.LIGHT)) { content() } }
        }
        compose.waitForIdle()
    }

    private fun inkOf(text: String): Color {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single().layoutInput.style.color
    }

    @Test
    fun overThePictureAPressTintsWhiteAndFocusRingsInTheStageAccent() {
        var page: IndicationTone? = null
        var stage: IndicationTone? = null
        show {
            page = LocalIndicationTone.current
            PlayerGestureLayer(player = null, onToggleControls = {}, onPinchFraming = {}) { stage = LocalIndicationTone.current }
        }
        assertEquals(Palette.LightText, page?.ink)
        assertEquals(IndicationTone(ink = Color.White, ring = StageAccent), stage)
    }

    @Test
    fun aMenuOpenedFromOverThePictureTintsInThePagesInkAgain() {
        var menu: IndicationTone? = null
        show {
            PlayerGestureLayer(player = null, onToggleControls = {}, onPinchFraming = {}) {
                CatalogueMenu(expanded = true, onDismissRequest = {}, still = true) { menu = LocalIndicationTone.current }
            }
        }
        assertEquals(Palette.LightText, menu?.ink)
    }

    @Test
    fun aToolTurnsToTheStageAccentWhilePressedAndBackOnRelease() {
        show { LabelButton(label = "Fit", description = "Framing", onClick = {}) }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Framing").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(300)
        assertEquals(StageAccent, inkOf("Fit"))
        compose.onNodeWithContentDescription("Framing").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(300)
        assertEquals(Color.White, inkOf("Fit"))
    }

    @Test
    fun aDimmedToolLightsTheSameWayAndGoesBackToDim() {
        show { GlyphButton(glyph = "CC", description = "Subtitles", enabled = true, onClick = {}, dimmed = true) }
        val dim = inkOf("CC")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Subtitles").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(300)
        assertEquals(StageAccent, inkOf("CC"))
        compose.onNodeWithContentDescription("Subtitles").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(300)
        assertEquals(dim, inkOf("CC"))
    }
}

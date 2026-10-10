package ui.player

import android.view.ViewConfiguration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The controls fade as the web's do. From the moment they start to go, a tap
 * meant for one of them reaches the picture instead (`pointer-events: none`,
 * `playback.css:63-70`): the control does nothing, and the picture brings
 * the controls back. A tablet's width, as in PlayerCardScreenTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp")
class PlayerHudFadeTest : PlayerCardScreenBase() {
    /** Taps the picture to put the controls away and stops 100ms into their 260ms fade. */
    private fun tapAwayAndStopMidFade() {
        compose.mainClock.autoAdvance = false
        compose.onRoot().performTouchInput { click(Offset(width * 0.1f, height * 0.4f)) }
        // The picture holds a single tap back for the double-tap timeout before acting on it.
        compose.mainClock.advanceTimeBy(ViewConfiguration.getDoubleTapTimeout() + 100L)
    }

    /** Past the picture's double-tap wait and past any fade, either way. */
    private fun settle() = compose.mainClock.advanceTimeBy(1_000)

    @Test
    fun theCardFadesOutRatherThanVanishing() {
        open()
        tapAwayAndStopMidFade()
        compose.onNodeWithTag(PlayerCardTag).assertExists()
        settle()
        compose.onNodeWithTag(PlayerCardTag).assertDoesNotExist()
    }

    @Test
    fun aTapOnAFadingToolReachesThePictureAndTheToolDoesNothing() {
        open()
        tapAwayAndStopMidFade()
        compose.onNodeWithContentDescription("Speed").performTouchInput { click() }
        settle()
        compose.onNodeWithTag(CardMenuTag).assertDoesNotExist()
        compose.onNodeWithTag(PlayerCardTag).assertExists()
    }

    @Test
    fun aTapOnAFadingMarkReachesThePictureAndTheMarkDoesNothing() {
        open()
        tapAwayAndStopMidFade()
        compose.onNodeWithText("Add to list").performTouchInput { click() }
        settle()
        compose.onNodeWithText("Done").assertDoesNotExist()
        compose.onNodeWithText("Add to list").assertExists()
    }
}

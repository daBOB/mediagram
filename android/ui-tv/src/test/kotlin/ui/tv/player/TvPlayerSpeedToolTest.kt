package ui.tv.player

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onNodeWithText
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Speed tool names the speed playing, as the web's speed button does
 * (`speedLabel(video.playbackRate)`), while its menu marks the speed chosen:
 * in watch together another screen can change the one without the other.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvPlayerSpeedToolTest : TvPlayerScreenHarness() {
    @Test
    fun speedSaysTheSpeedPlayingAndItsMenuMarksTheOneChosen() {
        compose.runOnUiThread { fixture.playAt(1.5f) }
        compose.waitForIdle()

        compose.onNodeWithText("1.5×").assertExists()

        openMenu("Speed")
        compose.onNode(hasText("1×") and isSelected()).assertExists()
    }
}

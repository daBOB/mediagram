package ui.tv.profile

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ui.tv.LeavesTouchModeRule
import ui.tv.TvTheme

/** Initial focus and D-pad travel on a real window manager; [TvPinPadStateTest] covers the rest without one. */
@RunWith(AndroidJUnit4::class)
class TvPinPadTest {
    @get:Rule val touchMode = LeavesTouchModeRule()

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(onPin: (String) -> Unit = {}) {
        compose.setContent { TvTheme { TvPinPad(enabled = true, onPin = onPin) } }
    }

    private fun press(key: Key) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test
    fun theRemoteStartsOnOneAndWalksTheGrid() {
        show()
        compose.onNodeWithTag(tvPinKeyTag("1")).assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithTag(tvPinKeyTag("2")).assertIsFocused()
        repeat(3) { press(Key.DirectionDown) }
        compose.onNodeWithTag(tvPinKeyTag("0")).assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithTag(tvPinKeyTag("Delete")).assertIsFocused()
    }

    @Test
    fun centreTypesAndTheFourthDigitHandsThePinOver() {
        var pin: String? = null
        show { pin = it }
        repeat(2) { press(Key.DirectionCenter) }
        press(Key.DirectionRight)
        repeat(2) { press(Key.DirectionCenter) }
        assertEquals("1122", pin)
    }
}

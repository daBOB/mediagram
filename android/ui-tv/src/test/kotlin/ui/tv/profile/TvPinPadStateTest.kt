package ui.tv.profile

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import catalog.profile.PinPrompt
import designsystem.Overscan
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.tv.catalog.TvScreenStateTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The PIN pad and its prompt driven by the remote alone: where it starts,
 * how the D-pad walks the grid, what Centre, the remote's digit keys and
 * "Delete" type, and that the whole prompt fits the 540dp-tall box it is
 * seen on. Out of touch mode, where a remote always is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvPinPadStateTest : TvScreenStateTest() {
    private val entered = mutableListOf<String>()
    private var cancelled = 0

    @Before
    fun leaveTouchMode() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    private fun prompt(
        prompt: PinPrompt = PinPrompt("andre’s PIN", newPin = false),
    ) = show { TvPinPrompt(prompt, onPin = { entered += it }, onCancel = { cancelled++ }) }

    private fun press(key: Key) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun dots() = compose.onNodeWithTag(TvPinDotsTag)

    @Test
    fun theRemoteStartsOnOneAndWalksTheWholeGrid() {
        prompt()
        compose.onNodeWithTag(tvPinKeyTag("1")).assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithTag(tvPinKeyTag("2")).assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithTag(tvPinKeyTag("3")).assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag(tvPinKeyTag("6")).assertIsFocused()
        press(Key.DirectionLeft)
        compose.onNodeWithTag(tvPinKeyTag("5")).assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag(tvPinKeyTag("8")).assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag(tvPinKeyTag("0")).assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithTag(tvPinKeyTag("Delete")).assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithText("Cancel").assertIsFocused()
    }

    @Test
    fun centreTypesTheKeyItRestsOnAndTheFourthDigitHandsThePinOver() {
        prompt()
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        dots().assertTextEquals("●●○○")
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        assertEquals(listOf("1122"), entered)
        // Empty again for a retry or a new PIN's second entry.
        dots().assertTextEquals("○○○○")
    }

    @Test
    fun deleteTakesTheLastDigitBack() {
        prompt()
        press(Key.DirectionCenter)
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        // To "Delete": down three rows from "2", then right.
        repeat(3) { press(Key.DirectionDown) }
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        dots().assertTextEquals("●○○○")
        assertTrue(entered.isEmpty())
    }

    /** A remote with digit keys types with them, and its delete key takes one back, wherever the remote rests on the pad. */
    @Test
    fun theRemotesOwnDigitKeysTypeToo() {
        prompt()
        listOf(Key.Two, Key.Four, Key.Backspace, Key.Six, Key.Eight, Key.NumPad0).forEach(::press)
        assertEquals(listOf("2680"), entered)
    }

    /** While a PIN is being checked the keys keep the remote but type nothing — a second PIN would race the first. */
    @Test
    fun aPromptBeingCheckedTypesNothing() {
        prompt(PinPrompt("andre’s PIN", newPin = false, busy = true))
        repeat(4) { press(Key.DirectionCenter) }
        press(Key.Five)
        dots().assertTextEquals("○○○○")
        assertTrue(entered.isEmpty())
        compose.onNodeWithTag(tvPinKeyTag("1")).assertIsFocused()
    }

    @Test
    fun thePromptSaysWhoseAndWhyTheLastTryFailed() {
        prompt(PinPrompt("andre’s PIN", newPin = false, error = "Wrong PIN."))
        compose.onNodeWithText("andre’s PIN").assertExists()
        compose.onNodeWithText("Wrong PIN.").assertExists()
    }

    @Test
    fun aNewPinsSecondEntryIsHeadedAsTheWebsFieldIs() {
        prompt(PinPrompt("A PIN for Ann", newPin = true, confirming = true))
        compose.onNodeWithText("The new PIN again").assertExists()
    }

    @Test
    fun cancelAndBackBothGiveUp() {
        prompt()
        repeat(4) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        assertEquals(1, cancelled)
        back()
        assertEquals(2, cancelled)
    }

    /** Heading, error, dots, four rows of keys and Cancel, inside the overscan of the 540dp-tall box this is seen on. */
    @Test
    fun thePadAndCancelFitA540dpTelevision() {
        prompt(PinPrompt("Choose a PIN for TV test", newPin = true, error = "The two PINs are not the same."))
        val screen = compose.onRoot().getBoundsInRoot()
        val cancel = compose.onNodeWithText("Cancel").getBoundsInRoot()
        assertTrue(cancel.bottom <= screen.bottom - Overscan.vertical + 1.dp, "Cancel ends at ${cancel.bottom}")
    }
}

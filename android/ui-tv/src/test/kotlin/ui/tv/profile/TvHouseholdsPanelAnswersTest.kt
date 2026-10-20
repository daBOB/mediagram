package ui.tv.profile

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import kotlinx.coroutines.channels.Channel
import model.HouseholdWords
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.tv.setup.TvTextQuestionFieldTag
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Only an answer moves a step on ([TvHouseholdsPanelHarness]): a PIN pad
 * given up answers nothing, and a change still out — held at the core here,
 * one call let through at a time — leaves its step where it is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvHouseholdsPanelAnswersTest : TvHouseholdsPanelHarness() {
    private val held = Channel<Unit>()

    /** Lets the next core call through. */
    private fun release() {
        compose.runOnUiThread { check(held.trySend(Unit).isSuccess) { "no core call was waiting" } }
        compose.waitForIdle()
    }

    /** Cy has no PIN, so running Flat asks one; Back on it is back on the choice, not a change made. */
    @Test
    fun backOnANewAdminsPinStaysOnTheChoices() {
        opened()
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        compose.onNodeWithText("A PIN for Cy").assertExists()

        back()
        row("Cy").assertIsFocused()
        compose.onNodeWithText("${HouseholdWords.CHANGE_ADMIN} · Flat").assertExists()
    }

    @Test
    fun backOnANewGrownUpsPinKeepsTheNames() {
        opened()
        repeat(2) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        name("Barn")
        press(Key.DirectionCenter)
        name("Eve")
        compose.onNodeWithText("A PIN for Eve").assertExists()

        back()
        compose.onNodeWithTag(TvTextQuestionFieldTag).assert(hasText("Eve"))
        compose.onNodeWithText("${HouseholdWords.CREATE_A_HOUSEHOLD} · Barn").assertExists()
    }

    /**
     * The owner watching: the first change asks their PIN. Once it is given
     * the step stays while the change is out, a second press sends nothing,
     * and the refusal is said on the step that sent it.
     */
    @Test
    fun aWatchingOwnersRefusedRenameIsSaidOnItsStep() {
        panel(watching = true)
        repeat(2) { press(Key.DirectionCenter) }
        name("home")
        compose.onNodeWithText(HouseholdWords.OWNERS_PIN).assertExists()
        provider.beforeCore = { held.receive() }
        pin("1234")
        release()

        compose.onNodeWithTag(TvTextQuestionFieldTag).assertExists()
        assertTrue(part().sending)
        compose.onNodeWithTag(TvTextQuestionFieldTag).performImeAction()
        compose.waitForIdle()
        release()

        compose.onNodeWithText(HouseholdWords.HOUSEHOLD_NAME_TAKEN).assertExists()
        assertEquals(1, part().answers)
        assertFalse(held.trySend(Unit).isSuccess, "a second rename went out while the first was")
    }
}

package ui.tv.profile

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import catalog.profile.ManageUiState
import model.HouseholdWords
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.tv.setup.TvTextQuestionFieldTag
import kotlin.test.assertEquals

/** Add someone on Manage profiles by remote ([TvManageProfilesHarness]): Grown-up or Kid where both may be added, then each in place. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvManageAddsStateTest : TvManageProfilesHarness() {
    /** Bea adds no grown-up: Add someone is a kid at once — its name, its limit starting on 6, its daily limit starting on none. */
    @Test
    fun addingAKidAsksItsNameThenItsLimitStartingOnSixThenItsDailyLimitStartingOnNone() {
        manage(ManageUiState.Managing(bea, emptyList(), emptyList()))
        press(Key.DirectionDown)
        row(HouseholdWords.ADD_SOMEONE).assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithTag(TvTextQuestionFieldTag).performTextInput("Lina")
        compose.onNodeWithTag(TvTextQuestionFieldTag).performImeAction()
        compose.waitForIdle()
        compose.onNodeWithTag(tvKidsLimitTag(6)).assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithTag(tvDailyLimitTag(null)).assertIsFocused()
        assertEquals(emptyList(), calls)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        assertEquals(listOf("addKid Lina 6 30"), calls)
        row(HouseholdWords.ADD_SOMEONE).assertExists()
    }

    @Test
    fun backFromTheLimitKeepsTheNameAndBackAgainLeavesTheAddingAlone() {
        manage(ManageUiState.Managing(bea, emptyList(), emptyList()))
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNodeWithTag(TvTextQuestionFieldTag).performTextInput("Lina")
        compose.onNodeWithTag(TvTextQuestionFieldTag).performImeAction()
        compose.waitForIdle()
        back()
        compose.onNodeWithText("Lina").assertExists()
        back()
        row("Bea").assertIsFocused()
        assertEquals(emptyList(), calls)
    }

    /** The admin's Add someone asks Grown-up or Kid first; a grown-up's name is handed on for its PIN. */
    @Test
    fun addingAGrownUpHandsTheNameOnForItsPin() {
        manage(ManageUiState.Managing(andre, emptyList(), emptyList()))
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        row(HouseholdWords.GROWN_UP).assertIsFocused()
        row(HouseholdWords.KID).assertExists()
        press(Key.DirectionCenter)
        compose.onNodeWithTag(TvTextQuestionFieldTag).performTextInput("Cleo")
        compose.onNodeWithTag(TvTextQuestionFieldTag).performImeAction()
        compose.waitForIdle()
        assertEquals(listOf("addGrownUp Cleo"), calls)
    }
}

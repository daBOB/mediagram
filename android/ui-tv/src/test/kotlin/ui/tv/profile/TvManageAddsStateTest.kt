package ui.tv.profile

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import catalog.profile.ManageUiState
import model.HouseholdWords
import model.Profile
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
        answer { it.copy(kids = it.kids + Profile("l", "Lina", kids = true, kidsAge = 6, parentId = "b")) }
        row("Lina").assertExists()
        row(HouseholdWords.ADD_SOMEONE).assertIsFocused()
    }

    /** A taken name is said on the step that sent it, and Back walks through the age and the name as they were chosen and typed. */
    @Test
    fun aRefusedAddKeepsWhatWasTypedAndSaysWhyOnTheAdd() {
        val taken = "That name is already used on this install."
        manage(ManageUiState.Managing(bea, emptyList(), listOf(tom)))
        repeat(2) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        compose.onNodeWithTag(TvTextQuestionFieldTag).performTextInput("Tom")
        compose.onNodeWithTag(TvTextQuestionFieldTag).performImeAction()
        compose.waitForIdle()
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        assertEquals(listOf("addKid Tom 12 30"), calls)

        answer { it.copy(notice = taken) }
        compose.onAllNodesWithText(taken).assertCountEquals(1)
        compose.onNodeWithTag(tvDailyLimitTag(30)).assertIsFocused()
        back()
        compose.onNodeWithTag(tvKidsLimitTag(12)).assertIsFocused()
        back()
        compose.onNodeWithTag(TvTextQuestionFieldTag).assert(hasText("Tom"))
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
        row(HouseholdWords.ADD_SOMEONE).assertIsFocused()
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

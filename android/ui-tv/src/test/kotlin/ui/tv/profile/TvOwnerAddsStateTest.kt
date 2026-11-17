package ui.tv.profile

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import catalog.profile.PinPrompt
import model.HouseholdWords
import model.Profile
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.tv.setup.TvTextQuestionFieldTag
import kotlin.test.assertEquals

/** An add from the owner's view, by remote ([TvOwnerViewHarness]): kept until it goes through, its refusal said where it was made. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvOwnerAddsStateTest : TvOwnerViewHarness() {
    /**
     * A refused add stays on the step that sent it, says why there, and keeps
     * what was chosen — across a PIN pad given up, too; the one that took
     * lands the remote back on its Add someone.
     */
    @Test
    fun aRefusedAddKeepsWhatWasChosenAndSaysWhyOnItsStep() {
        walkTo(hasText("Flat · Admin Maja", substring = true))
        walkTo(ownersAddSomeone)
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        name("Tim")
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        onAdd = { prompt.value = PinPrompt("andre’s PIN", newPin = false) }
        press(Key.DirectionCenter)
        compose.onNodeWithText("andre’s PIN").assertExists()
        back()
        compose.onNodeWithTag(tvDailyLimitTag(30)).assertIsFocused()

        onAdd = { change { it.copy(notice = "That name is taken.") } }
        press(Key.DirectionCenter)
        compose.onNodeWithText("That name is taken.").assertExists()
        compose.onNodeWithTag(tvDailyLimitTag(30)).assertIsFocused()
        back()
        compose.onNodeWithTag(tvKidsLimitTag(12)).assertIsFocused()
        back()
        compose.onNodeWithTag(TvTextQuestionFieldTag).assert(hasText("Tim"))

        onAdd = { change { it.copy(notice = null, kids = it.kids + Profile("tia", "Tia", kids = true, kidsAge = 12, parentId = "maja", household = "flat")) } }
        name("Tia")
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        assertEquals(listOf("kid Tim 12 30 maja", "kid Tim 12 30 maja", "kid Tia 12 30 maja"), calls)
        compose.onNode(hasContentDescription("${HouseholdWords.ADD_SOMEONE_TO} Flat")).assertIsFocused()
        assertEquals(TvHouseholdStep.List("flat"), went.last())
    }

    /** An add given up takes its refusal with it: neither the page nor the next Add someone says it again. */
    @Test
    fun aCancelledAddForgetsItsRefusal() {
        walkTo(hasText("Flat · Admin Maja", substring = true))
        walkTo(ownersAddSomeone)
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        name("Tim")
        press(Key.DirectionCenter)
        onAdd = { change { it.copy(notice = "That name is taken.") } }
        press(Key.DirectionCenter)
        compose.onNodeWithText("That name is taken.").assertExists()

        repeat(4) { back() }
        compose.onNode(hasContentDescription("${HouseholdWords.ADD_SOMEONE_TO} Flat")).assertIsFocused()
        compose.onAllNodesWithText("That name is taken.").assertCountEquals(0)
        walkTo(ownersAddSomeone)
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNodeWithTag(TvTextQuestionFieldTag).assertExists()
        compose.onAllNodesWithText("That name is taken.").assertCountEquals(0)
    }

    /** A household its list does not name yet keeps its people, as on the web, but its Add someone names no raw id. */
    @Test
    fun anUnnamedHouseholdsAddSomeoneNamesNoHousehold() {
        change { it.copy(grownUps = it.grownUps + Profile("ned", "Ned", admin = true, hasPin = true, household = "barn")) }
        walkTo(hasText("Ned"))
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNodeWithText(HouseholdWords.ADD_SOMEONE).assertExists()
        compose.onAllNodesWithText("barn", substring = true).assertCountEquals(0)
    }
}

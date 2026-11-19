package ui.tv.profile

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import catalog.profile.CHANGE_YOUR_PIN
import catalog.profile.ManageUiState
import catalog.profile.PinPrompt
import model.HouseholdWords
import model.Profile
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Manage profiles on television by remote ([TvManageProfilesHarness]): Who's
 * managing?, then what your role lets you change — one calm row per person,
 * the remote landing on your own, whose choices open in a dialog.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvManageProfilesStateTest : TvManageProfilesHarness() {
    @Test
    fun whoIsManagingOffersTheGrownUpsAsTilesAndSaysWhyItAsksAgain() {
        manage(ManageUiState.ChoosingActor(listOf(andre, bea), notice = "Your PIN is no longer valid. Choose who you are again."))
        compose.onNodeWithText(HouseholdWords.WHOS_MANAGING).assertExists()
        compose.onNodeWithText("Your PIN is no longer valid. Choose who you are again.").assertExists()
        compose.onNodeWithTag(tvManagerTileTag("a")).assertIsFocused()
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        assertEquals(listOf("actAs b"), calls)
    }

    @Test
    fun aGrownUpSeesItsOwnKidsAndAKidsRowChangesItsLimit() {
        manage(ManageUiState.Managing(bea, emptyList(), listOf(tom)))
        compose.onNodeWithText("As Bea").assertExists()
        compose.onNodeWithText("Grown-ups").assertDoesNotExist()
        row("Bea").assertIsFocused().assert(hasText("Grown-up · PIN set"))
        press(Key.DirectionDown)
        row("Tom").assertIsFocused().assert(hasText("Kid · Bea · FSK 12"))
        press(Key.DirectionCenter)
        // The current limit holds the remote; the other is one step up.
        compose.onNode(hasText("FSK 12") and hasAnyAncestor(isDialog())).assertIsFocused()
        press(Key.DirectionUp)
        press(Key.DirectionCenter)
        assertEquals(listOf("age t 6"), calls)
        row("Tom").assertIsFocused()
    }

    @Test
    fun aKidsDailyLimitOpensItsOwnDialogHoldingTheCurrentChoice() {
        val limited = tom.copy(dailyLimit = 90)
        manage(ManageUiState.Managing(bea, emptyList(), listOf(limited)))
        press(Key.DirectionDown)
        row("Tom").assertIsFocused().assert(hasText("Kid · Bea · FSK 12 · 1 h 30 min"))
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        compose.onNode(hasText("Daily limit · 1 h 30 min") and hasAnyAncestor(isDialog())).assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNode(hasText("1 h 30 min") and hasAnyAncestor(isDialog())).assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(emptyList(), calls, "the choice already set sends nothing")
        row("Tom").assertIsFocused()
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        press(Key.DirectionUp)
        press(Key.DirectionCenter)
        assertEquals(listOf("daily t 60"), calls)
    }

    @Test
    fun aKidIsRemovedOnlyOnceTheQuestionIsAnswered() {
        manage(ManageUiState.Managing(bea, emptyList(), listOf(tom)))
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        // FSK 12 -> Daily limit -> Sections -> Remove.
        repeat(3) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        compose.onNodeWithText("Remove Tom and everything they have watched?").assertExists()
        // Cancel holds the remote first: Centre now would remove nobody.
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        assertEquals(listOf("remove t"), calls)
    }

    @Test
    fun theAdminRemovesAGrownUpAfterBeingToldItsKidsGoToo() {
        manage(ManageUiState.Managing(andre, listOf(bea), emptyList()))
        compose.onNodeWithText("Grown-ups").assertDoesNotExist()
        row("andre").assertIsFocused()
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNode(hasText("Reset PIN") and hasAnyAncestor(isDialog())).assertIsFocused()
        repeat(2) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        compose.onNodeWithText("Remove Bea, their kids, and everything they have watched?").assertExists()
        press(Key.DirectionCenter)
        assertEquals(emptyList(), calls, "Cancel holds the remote first")
        row("Bea").assertIsFocused()
    }

    @Test
    fun theAdminResetsAnotherGrownUpsPin() {
        manage(ManageUiState.Managing(andre, listOf(bea), emptyList()))
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        assertEquals(listOf("pin b"), calls)
    }

    /** A PIN pad stands in for the rows while it asks; once it is done, the remote is back on the person it was asked for. */
    @Test
    fun afterAPinPadTheRemoteIsBackOnThatPerson() {
        manage(ManageUiState.Managing(andre, listOf(bea), emptyList()))
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        assertEquals(listOf("pin b"), calls)
        compose.runOnUiThread { prompt.value = PinPrompt("A new PIN for Bea", newPin = true) }
        compose.onNodeWithText("A new PIN for Bea").assertExists()
        compose.runOnUiThread { prompt.value = null }
        compose.waitForIdle()
        row("Bea").assertIsFocused()
    }

    /** The manager's own row says what OK on it does — Change your PIN — and the bottom of a long panel is reached by walking down. */
    @Test
    fun theOwnRowSaysChangeYourPinAndDoneIsReachedByWalkingDown() {
        val kids = (1..5).map { Profile("k$it", "Kid $it", kids = true, kidsAge = 6, parentId = "a") }
        manage(ManageUiState.Managing(andre, listOf(bea), kids))
        press(Key.DirectionCenter)
        compose.onNode(hasText(CHANGE_YOUR_PIN) and hasAnyAncestor(isDialog())).assertIsFocused()
        press(Key.DirectionCenter)
        repeat(1 + kids.size + 1 + 1) { press(Key.DirectionDown) }
        compose.onNodeWithText("Done").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(listOf("pin a", "close"), calls)
    }

    @Test
    fun aRefusalIsSaidOnThePanelAndBackIsDone() {
        manage(ManageUiState.Managing(bea, emptyList(), listOf(tom), notice = "A profile with that name already exists."))
        compose.onNodeWithText("A profile with that name already exists.").assertExists()
        back()
        assertEquals(listOf("close"), calls)
    }
}

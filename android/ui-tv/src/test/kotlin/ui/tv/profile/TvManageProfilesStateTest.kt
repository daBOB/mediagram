package ui.tv.profile

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.test.platform.app.InstrumentationRegistry
import catalog.profile.ManageUiState
import model.Profile
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.tv.catalog.TvScreenStateTest
import ui.tv.setup.TvTextQuestionFieldTag
import kotlin.test.assertEquals

/**
 * Manage profiles on television, over plain state and recorded actions,
 * driven by the remote: who you are, then what your role lets you change —
 * each row's few choices in a dialog, adding in place. Out of touch mode,
 * where a remote always is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvManageProfilesStateTest : TvScreenStateTest() {
    private val andre = Profile("a", "andre", admin = true, hasPin = true)
    private val bea = Profile("b", "Bea", hasPin = true)
    private val tom = Profile("t", "Tom", kids = true, kidsAge = 12, parentId = "b")
    private val calls = mutableListOf<String>()
    private val actions =
        TvManageActions(
            onActAs = { calls += "actAs $it" },
            onAddKid = { name, age -> calls += "addKid $name $age" },
            onSetKidsAge = { id, age -> calls += "age $id $age" },
            onRemove = { calls += "remove $it" },
            onAddGrownUp = { calls += "addGrownUp $it" },
            onChangePin = { calls += "pin $it" },
            onClose = { calls += "close" },
        )

    @Before
    fun leaveTouchMode() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    private fun manage(state: ManageUiState) = show { TvManageProfiles(state, actions) }

    /** A key to whatever holds the remote — the dialog's window first, while one is open. */
    private fun press(key: Key) {
        val inDialog = isFocused() and hasAnyAncestor(isDialog())
        val target = if (compose.onAllNodes(inDialog).fetchSemanticsNodes().isNotEmpty()) inDialog else isFocused()
        compose.onNode(target).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test
    fun whoAreYouOffersTheGrownUpsAndSaysWhyItAsksAgain() {
        manage(ManageUiState.ChoosingActor(listOf(andre, bea), notice = "Your PIN is no longer valid. Choose who you are again."))
        compose.onNodeWithText("Who are you?").assertExists()
        compose.onNodeWithText("Your PIN is no longer valid. Choose who you are again.").assertExists()
        compose.onNodeWithText("andre").assertIsFocused()
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        assertEquals(listOf("actAs b"), calls)
    }

    @Test
    fun aGrownUpSeesItsOwnKidsAndAKidsRowChangesItsLimit() {
        manage(ManageUiState.Managing(bea, emptyList(), listOf(tom)))
        compose.onNodeWithText("As Bea").assertExists()
        compose.onNodeWithText("Grown-ups").assertDoesNotExist()
        compose.onNodeWithText("Add a grown-up").assertDoesNotExist()
        compose.onNodeWithText("Tom · FSK 12").assertIsFocused()
        press(Key.DirectionCenter)
        // The current limit holds the remote; the other is one step up.
        compose.onNode(hasText("FSK 12") and hasAnyAncestor(isDialog())).assertIsFocused()
        press(Key.DirectionUp)
        press(Key.DirectionCenter)
        assertEquals(listOf("age t 6"), calls)
        compose.onNodeWithText("Tom · FSK 12").assertIsFocused()
    }

    @Test
    fun aKidIsRemovedOnlyOnceTheQuestionIsAnswered() {
        manage(ManageUiState.Managing(bea, emptyList(), listOf(tom)))
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
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
        compose.onNodeWithText("Grown-ups").assertExists()
        compose.onNodeWithText("Bea").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNode(hasText("Reset PIN") and hasAnyAncestor(isDialog())).assertIsFocused()
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNodeWithText("Remove Bea, their kids, and everything they have watched?").assertExists()
        press(Key.DirectionCenter)
        assertEquals(emptyList(), calls, "Cancel holds the remote first")
        compose.onNodeWithText("Bea").assertIsFocused()
    }

    @Test
    fun theAdminResetsAnotherGrownUpsPin() {
        manage(ManageUiState.Managing(andre, listOf(bea), emptyList()))
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        assertEquals(listOf("pin b"), calls)
    }

    @Test
    fun addingAKidAsksItsNameThenItsLimitStartingOnSix() {
        manage(ManageUiState.Managing(bea, emptyList(), emptyList()))
        compose.onNodeWithText("Add a kid").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithTag(TvTextQuestionFieldTag).performTextInput("Lina")
        compose.onNodeWithTag(TvTextQuestionFieldTag).performImeAction()
        compose.waitForIdle()
        compose.onNodeWithTag(tvKidsLimitTag(6)).assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(listOf("addKid Lina 6"), calls)
        compose.onNodeWithText("Add a kid").assertExists()
    }

    @Test
    fun backFromTheLimitKeepsTheNameAndBackAgainLeavesTheAddingAlone() {
        manage(ManageUiState.Managing(bea, emptyList(), emptyList()))
        press(Key.DirectionCenter)
        compose.onNodeWithTag(TvTextQuestionFieldTag).performTextInput("Lina")
        compose.onNodeWithTag(TvTextQuestionFieldTag).performImeAction()
        compose.waitForIdle()
        back()
        compose.onNodeWithText("Lina").assertExists()
        back()
        compose.onNodeWithText("Add a kid").assertIsFocused()
        assertEquals(emptyList(), calls)
    }

    @Test
    fun addingAGrownUpHandsTheNameOnForItsPin() {
        manage(ManageUiState.Managing(andre, emptyList(), emptyList()))
        compose.onNodeWithText("Add a grown-up").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithTag(TvTextQuestionFieldTag).performTextInput("Cleo")
        compose.onNodeWithTag(TvTextQuestionFieldTag).performImeAction()
        compose.waitForIdle()
        assertEquals(listOf("addGrownUp Cleo"), calls)
    }

    /** The bottom of a long panel is reached by walking down — the page scrolls with the remote. */
    @Test
    fun yourPinAndDoneAreReachedByWalkingDown() {
        val kids = (1..5).map { Profile("k$it", "Kid $it", kids = true, kidsAge = 6, parentId = "a") }
        manage(ManageUiState.Managing(andre, listOf(bea), kids))
        repeat(1 + 1 + kids.size + 1) { press(Key.DirectionDown) }
        compose.onNodeWithText("Change your PIN").assertIsFocused()
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
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

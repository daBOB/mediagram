package ui.tv.profile

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.style.TextDecoration
import androidx.test.platform.app.InstrumentationRegistry
import catalog.profile.ManageUiState
import model.Profile
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.common.profile.ManageActions
import ui.tv.catalog.TvScreenStateTest
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Manage profiles' Sections on television, over plain state and recorded
 * actions, by remote alone: a row's choices hold a Sections line between its
 * limits and Remove, which opens a switch per section — on while the section
 * is shown, as on the web. A press sends the whole list and the dialog stays,
 * the remote on the switch it pressed; Done closes it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvManageSectionsStateTest : TvScreenStateTest() {
    private val andre = Profile("a", "andre", admin = true, hasPin = true)
    private val bea = Profile("b", "Bea", hasPin = true)
    private val tom = Profile("t", "Tom", kids = true, kidsAge = 12, parentId = "b", hiddenSections = listOf("anime", "later"))
    private val calls = mutableListOf<String>()
    private val actions =
        ManageActions(
            onActAs = { calls += "actAs $it" },
            onAddKid = { name, age, daily -> calls += "addKid $name $age $daily" },
            onSetKidsAge = { id, age -> calls += "age $id $age" },
            onSetDailyLimit = { id, minutes -> calls += "daily $id $minutes" },
            onSetHiddenSections = { id, sections -> calls += "sections $id $sections" },
            onRemove = { calls += "remove $it" },
            onAddGrownUp = { calls += "addGrownUp $it" },
            onChangePin = { calls += "pin $it" },
            onClose = { calls += "close" },
        )
    private val inDialog = hasAnyAncestor(isDialog())
    private val shown = mutableStateOf<ManageUiState>(ManageUiState.Closed)

    @Before
    fun leaveTouchMode() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    private fun manage(state: ManageUiState) {
        shown.value = state
        show { TvManageProfiles(shown.value, actions) }
    }

    /** What the panel would draw next: [tom] as the core now holds him, and why the last change did not take. */
    private fun answer(hidden: List<String>, notice: String? = null) =
        compose.runOnUiThread { shown.value = ManageUiState.Managing(bea, emptyList(), listOf(tom.copy(hiddenSections = hidden)), notice) }

    /** Tom's row → Sections, which says what is hidden as the Everyone line does: FSK 12 → Daily limit → Sections. */
    private fun openTomsSections() {
        manage(ManageUiState.Managing(bea, emptyList(), listOf(tom)))
        press(Key.DirectionCenter)
        repeat(2) { press(Key.DirectionDown) }
        switch("Sections · hides Anime").assertIsFocused()
        press(Key.DirectionCenter)
    }

    private fun switch(label: String) = compose.onNode(hasText(label) and inDialog)

    /** A key to whatever holds the remote — the dialog's window first, while one is open. */
    private fun press(key: Key) {
        val target: SemanticsMatcher = if (compose.onAllNodes(isFocused() and inDialog).fetchSemanticsNodes().isNotEmpty()) isFocused() and inDialog else isFocused()
        compose.onNode(target).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    /** A press sends the whole list, an unknown key kept; the next does nothing until the answer, then builds on the new list. */
    @Test
    fun aKidsSectionsAreSwitchesAndASecondPressWaitsForTheFirstsAnswer() {
        openTomsSections()
        compose.onNodeWithText("Sections · Tom").assertExists()
        compose.onNode(hasContentDescription("Sections for Tom") and inDialog).assertExists()
        switch("Movies").assertIsFocused().assertIsOn().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        repeat(2) { press(Key.DirectionDown) }
        switch("Anime").assertIsFocused().assertIsOff()
        press(Key.DirectionCenter)
        switch("Anime").assertIsFocused()
        press(Key.DirectionUp)
        press(Key.DirectionCenter)
        assertEquals(listOf("sections t [later]"), calls)
        answer(listOf("later"))
        switch("Series").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(listOf("sections t [later]", "sections t [later, series]"), calls)
    }

    /** A refusal is said in the dialog, and frees the switches for another try. */
    @Test
    fun aRefusalIsSaidInTheDialogAndFreesTheSwitches() {
        openTomsSections()
        press(Key.DirectionCenter)
        answer(listOf("anime", "later"), notice = "That is not allowed.")
        compose.onNode(hasText("That is not allowed.") and inDialog).assertExists()
        switch("Movies").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(listOf("sections t [anime, later, movies]", "sections t [anime, later, movies]"), calls)
    }

    /** The look the user chose: a section the profile sees is a quiet line pill; only a hidden one is filled and struck through. */
    @Test
    fun aHiddenSectionIsStruckThroughAndAShownOneIsNot() {
        openTomsSections()
        assertEquals(TextDecoration.LineThrough, styleOf("Anime").textDecoration)
        assertNotEquals(TextDecoration.LineThrough, styleOf("Movies").textDecoration)
    }

    @Test
    fun theAdminTurnsAGrownUpsSectionsAndDoneGoesBackToItsRow() {
        manage(ManageUiState.Managing(andre, listOf(bea), emptyList()))
        press(Key.DirectionCenter)
        // Reset PIN -> Sections; Remove stays last.
        press(Key.DirectionDown)
        switch("Sections").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText("Sections · Bea").assertExists()
        repeat(4) { press(Key.DirectionDown) }
        switch("Tutorials").assertIsFocused()
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        switch("Done").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(listOf("sections b [tutorials]"), calls)
        compose.onNodeWithText("Bea").assertIsFocused()
    }
}

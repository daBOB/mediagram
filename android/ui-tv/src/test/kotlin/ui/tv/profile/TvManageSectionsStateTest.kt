package ui.tv.profile

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
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

    @Before
    fun leaveTouchMode() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    private fun manage(state: ManageUiState) = show { TvManageProfiles(state, actions) }

    private fun switch(label: String) = compose.onNode(hasText(label) and inDialog)

    /** A key to whatever holds the remote — the dialog's window first, while one is open. */
    private fun press(key: Key) {
        val target: SemanticsMatcher = if (compose.onAllNodes(isFocused() and inDialog).fetchSemanticsNodes().isNotEmpty()) isFocused() and inDialog else isFocused()
        compose.onNode(target).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test
    fun aKidsSectionsAreSwitchesAndAPressKeepsTheRemoteOnIt() {
        manage(ManageUiState.Managing(bea, emptyList(), listOf(tom)))
        press(Key.DirectionCenter)
        // FSK 12 -> Daily limit -> Sections, which says what is hidden as the Everyone line does.
        repeat(2) { press(Key.DirectionDown) }
        switch("Sections · hides Anime").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText("Sections · Tom").assertExists()
        switch("Movies").assertIsFocused().assertIsOn().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        repeat(2) { press(Key.DirectionDown) }
        switch("Anime").assertIsFocused().assertIsOff()
        press(Key.DirectionCenter)
        switch("Anime").assertIsFocused()
        press(Key.DirectionUp)
        press(Key.DirectionCenter)
        assertEquals(listOf("sections t [later]", "sections t [anime, later, series]"), calls)
    }

    /** The look the user chose: a section the profile sees is a quiet line pill; only a hidden one is filled and struck through. */
    @Test
    fun aHiddenSectionIsStruckThroughAndAShownOneIsNot() {
        manage(ManageUiState.Managing(bea, emptyList(), listOf(tom)))
        press(Key.DirectionCenter)
        repeat(2) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
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

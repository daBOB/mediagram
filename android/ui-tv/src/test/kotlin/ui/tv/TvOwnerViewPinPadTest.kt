package ui.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import catalog.profile.CHANGE_YOUR_PIN
import catalog.profile.MANAGE_PROFILES
import catalog.profile.RESET_PIN
import catalog.profile.managingAs
import model.HouseholdWords
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.tv.profile.TvManageProfilesTag
import ui.tv.profile.tvManagerTileTag
import kotlin.test.assertEquals

/**
 * Manage's PIN pads in the owner's view on television, by remote alone ([TvProfilesHarness]) over
 * [twoHouseholds]: a pad stands in for the rows while it asks, then gives the remote back to the
 * person it was asked from — as the web gives focus back to the control that changed. Back to
 * Who's managing?'s tiles is a fresh question, which asks the owner's PIN again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvOwnerViewPinPadTest : TvProfilesHarness() {
    private fun inDialog(text: String) = hasText(text) and hasAnyAncestor(isDialog())

    /** The picker's Manage profiles → the owner's tile, first → the owner's PIN. */
    private fun ownersViewFromThePicker() {
        launch(emptyList(), chosen = null, seed = twoHouseholds())
        walkTo(hasTestTag(TvManageProfilesTag), Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNode(hasTestTag(tvManagerTileTag("a"))).assertIsFocused()
        press(Key.DirectionCenter)
        pin("1234")
        compose.onNodeWithText(managingAs("andre")).assertExists()
    }

    /** OK on [name]'s row, then [choice] in their dialog. */
    private fun choose(
        name: String,
        choice: String,
    ) {
        walkTo(rowOf(name), Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNode(inDialog(choice)).assertIsFocused()
        press(Key.DirectionCenter)
    }

    @Test
    fun aResetPinLandsTheRemoteBackOnThatPerson() {
        ownersViewFromThePicker()
        choose("Bo", RESET_PIN)
        compose.onNodeWithText("A new PIN for Bo").assertExists()
        pin("2468")
        pin("2468")
        assertEquals("2468", fixture.core.roles.pins["b"])
        compose.onNode(rowOf("Bo")).assertIsFocused()
    }

    @Test
    fun changingYourPinLandsTheRemoteBackOnYourRow() {
        ownersViewFromThePicker()
        choose("andre", CHANGE_YOUR_PIN)
        compose.onNodeWithText("Your new PIN").assertExists()
        pin("1357")
        pin("1357")
        assertEquals("1357", fixture.core.roles.pins["a"])
        compose.onNode(rowOf("andre")).assertIsFocused()
    }

    /** The owner watching, from Settings: the visit's first change asks the owner's PIN, then its own — and the remote is back on that person. */
    @Test
    fun theOwnersFirstChangeFromSettingsLandsBackOnThatPerson() {
        launch(emptyList(), chosen = "a", seed = twoHouseholds())
        openSettingsAt(rowOf("Profiles"))
        press(Key.DirectionCenter)
        walkTo(rowOf(MANAGE_PROFILES), Key.DirectionDown)
        press(Key.DirectionCenter)
        choose("Bo", RESET_PIN)
        compose.onNodeWithText(HouseholdWords.OWNERS_PIN).assertExists()
        pin("1234")
        compose.onNodeWithText("A new PIN for Bo").assertExists()
        pin("2468")
        pin("2468")
        compose.onNode(rowOf("Bo")).assertIsFocused()
    }

    @Test
    fun backToTheTilesThenTheOwnersTileAsksThePinAgain() {
        ownersViewFromThePicker()
        back()
        compose.onNode(hasTestTag(tvManagerTileTag("a"))).assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText(HouseholdWords.OWNERS_PIN).assertExists()
    }
}

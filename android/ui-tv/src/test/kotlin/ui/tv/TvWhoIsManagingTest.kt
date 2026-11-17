package ui.tv

import androidx.activity.ComponentDialog
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import catalog.profile.CHANGE_YOUR_PIN
import catalog.profile.EVERYONE_HERE
import catalog.profile.REMOVE
import catalog.profile.RESET_PIN
import catalog.profile.managingAs
import model.HouseholdWords
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import testing.FakeCore
import ui.tv.profile.TvManageProfilesTag
import ui.tv.profile.tvManagerTileTag
import kotlin.test.assertEquals
import uniffi.mediagram_core.Profile as CoreProfile

/**
 * Who's managing? and the calm owner's view on television, by remote alone ([TvProfilesHarness]) over
 * [twoHouseholds] with Mia, Bo's kid, at Home: the picker's Manage profiles asks who is managing with the
 * picker's own tiles, the owner first and labelled Owner; the owner's tile asks the owner's PIN and opens
 * every household as one row per person, whose OK opens their controls, and one Add someone each, which
 * asks Grown-up or Kid first. What an add sends is read back from the core.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvWhoIsManagingTest : TvProfilesHarness() {
    private val home = "Home · Admin andre · 3 profiles"
    private val flat = "Flat · Admin Dan · 1 profile"
    private val withMia: FakeCore.() -> Unit = {
        twoHouseholds()(this)
        profiles = profiles + CoreProfile("m", "Mia", kids = true, kidsAge = 6u, parentId = "b")
    }

    private fun tile(id: String) = hasTestTag(tvManagerTileTag(id))

    private fun inDialog(text: String) = hasText(text) and hasAnyAncestor(isDialog())

    /** The picker's Manage profiles, by remote: Who's managing?, the remote on the first tile. */
    private fun whoIsManaging() {
        launch(emptyList(), chosen = null, seed = withMia)
        walkTo(hasTestTag(TvManageProfilesTag), Key.DirectionDown)
        press(Key.DirectionCenter)
    }

    /** Who's managing? → the owner's tile → the owner's PIN: the owner's view. */
    private fun ownersView() {
        whoIsManaging()
        compose.onNode(tile("a")).assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText(HouseholdWords.OWNERS_PIN).assertExists()
        pin("1234")
        compose.onNodeWithText(managingAs("andre")).assertExists()
    }

    /** The Back key of the dialog's own window, as the remote's lands there while a panel is open. */
    private fun backInTheDialog() {
        compose.runOnUiThread { (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    /** One question, as Who's watching? asks: the grown-ups' tiles, the owner first and labelled Owner; no kid, no list, no This device. */
    @Test
    fun managingAsksWhoWithThePickersTiles() {
        whoIsManaging()
        compose.onNodeWithText(HouseholdWords.WHOS_MANAGING).assertExists()
        compose.onNode(tile("a")).assertIsFocused().assert(hasText(HouseholdWords.OWNER))
        press(Key.DirectionRight)
        compose.onNode(tile("b")).assertIsFocused()
        compose.onAllNodes(tile("m")).assertCountEquals(0)
        compose.onAllNodesWithText(HouseholdWords.OWNER).assertCountEquals(1)
        compose.onAllNodesWithText(EVERYONE_HERE).assertCountEquals(0)
        compose.onAllNodesWithText(HouseholdWords.THIS_DEVICE, substring = true).assertCountEquals(0)

        back()
        compose.onNodeWithText("Who's watching?").assertExists()
        compose.onNodeWithTag(TvManageProfilesTag).assertIsFocused()
    }

    /** The owner's tile asks the owner's PIN, then every household: this device's first, the remote on its header. */
    @Test
    fun ownerTileOpensTheOwnerPanel() {
        ownersView()
        compose.onNode(rowOf(home)).assertIsFocused()
        compose.onNode(rowOf(flat)).assertExists()
        compose.onAllNodesWithText(HouseholdWords.WHOS_MANAGING).assertCountEquals(0)
    }

    /** Back from the owner's view is Who's managing? again, the remote on the tile that opened it; Back again is the picker. */
    @Test
    fun backFromTheOwnersViewReturnsToTheTileThatOpenedIt() {
        ownersView()
        back()
        compose.onNodeWithText(HouseholdWords.WHOS_MANAGING).assertExists()
        compose.onNode(tile("a")).assertIsFocused()
        back()
        compose.onNodeWithText("Who's watching?").assertExists()
        compose.onNodeWithTag(TvManageProfilesTag).assertIsFocused()
    }

    /** A person's row is their name and line; OK opens what may be changed of them, and what is chosen there is sent. */
    @Test
    fun personRowOpensTheirControlsPanel() {
        ownersView()
        walkTo(rowOf("Bo"), Key.DirectionDown)
        compose.onNode(rowOf("Bo")).assert(hasText("Grown-up · PIN set"))
        press(Key.DirectionCenter)
        compose.onNode(inDialog(RESET_PIN)).assertIsFocused()
        compose.onNode(inDialog(REMOVE)).assertExists()
        press(Key.DirectionCenter)
        compose.onNodeWithText("A new PIN for Bo").assertExists()
    }

    /** The owner's own row opens a panel that says what it does: Change your PIN. */
    @Test
    fun ownRowOpensAPanelThatSaysChangeYourPin() {
        ownersView()
        press(Key.DirectionDown)
        compose.onNode(rowOf("andre")).assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNode(inDialog(CHANGE_YOUR_PIN)).assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText("Your new PIN").assertExists()
    }

    /** Mia is Bo's to change, not the owner's: her line is shown, but the remote passes over it and OK opens nothing. */
    @Test
    fun readOnlyPersonRowDoesNotOpen() {
        ownersView()
        compose.onNodeWithText("Mia").assertExists()
        compose.onNodeWithText("Kid · Bo · FSK 6").assertExists()
        compose.onAllNodes(rowOf("Mia")).assertCountEquals(0)
        walkTo(rowOf("Bo"), Key.DirectionDown)
        press(Key.DirectionDown)
        compose.onNode(hasContentDescription("${HouseholdWords.ADD_SOMEONE_TO} Home")).assertIsFocused()
    }

    /** One Add someone per household asks Grown-up or Kid first; a kid at Home, with two grown-ups, then asks whose it is. */
    @Test
    fun addSomeoneAsksGrownUpOrKidFirst() {
        ownersView()
        compose.onAllNodes(rowOf(HouseholdWords.ADD_SOMEONE)).assertCountEquals(2)
        walkTo(rowOf(HouseholdWords.ADD_SOMEONE), Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNodeWithText("${HouseholdWords.ADD_SOMEONE} · Home").assertExists()
        compose.onNode(rowOf(HouseholdWords.GROWN_UP)).assertIsFocused()
        press(Key.DirectionDown)
        compose.onNode(rowOf(HouseholdWords.KID)).assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText(HouseholdWords.BELONGS_TO).assertExists()
        compose.onNode(rowOf("andre")).assertIsFocused()
        back()
        compose.onNode(rowOf(HouseholdWords.KID)).assertIsFocused()
    }

    /** Flat's Add someone makes a grown-up in Flat, not here, and the remote is back on Flat's header. */
    @Test
    fun addSomeoneUnderAHouseholdSendsThatHousehold() {
        ownersView()
        walkTo(rowOf(flat), Key.DirectionDown)
        walkTo(rowOf(HouseholdWords.ADD_SOMEONE), Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNodeWithText("${HouseholdWords.ADD_SOMEONE} · Flat").assertExists()
        press(Key.DirectionCenter)
        name("Eve")
        compose.onNodeWithText("A PIN for Eve").assertExists()
        pin("2468")
        pin("2468")

        assertEquals("flat", fixture.core.profiles.single { it.name == "Eve" }.household)
        compose.onNode(rowOf("Flat · Admin Dan · 2 profiles")).assertIsFocused()
    }

    /** Back from a person's panel is back on their row, not on the household's header. */
    @Test
    fun backFromAPersonReturnsToTheirRow() {
        ownersView()
        walkTo(rowOf("Bo"), Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNode(inDialog(RESET_PIN)).assertIsFocused()
        backInTheDialog()
        compose.onAllNodes(inDialog(RESET_PIN)).assertCountEquals(0)
        compose.onNode(rowOf("Bo")).assertIsFocused()
    }
}

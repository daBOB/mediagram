package ui.tv.profile

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithText
import model.HouseholdWords
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** The owner's Households panel by remote alone ([TvHouseholdsPanelHarness]): the owner's PIN first, then the households and their steps. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvHouseholdsPanelStateTest : TvHouseholdsPanelHarness() {
    @Test
    fun theOwnerRowAsksThePinThenListsHouseholds() {
        panel()
        compose.onNodeWithText(HouseholdWords.OWNERS_PIN).assertExists()
        pin("1234")
        row("Flat · Admin Bea · 2 profiles").assertIsFocused()
        row("Home · Admin andre · 1 profile").assertExists()
        row(HouseholdWords.CREATE_A_HOUSEHOLD).assertExists()
    }

    @Test
    fun givingUpTheOwnersPinLeavesThePanel() {
        panel()
        back()
        assertEquals(1, left)
    }

    /** Flat: a new name, another admin, this device; not empty, so no Delete. Home is the owner's own: no admin to change. */
    @Test
    fun okOnARowOffersOnlyWhatItAllows() {
        opened()
        press(Key.DirectionCenter)
        row(HouseholdWords.RENAME).assertIsFocused()
        row(HouseholdWords.CHANGE_ADMIN).assertExists()
        row(HouseholdWords.USE_HERE).assertExists()
        noRow(HouseholdWords.MOVE_HERE)
        noRow(HouseholdWords.DELETE)

        back()
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        row(HouseholdWords.RENAME).assertIsFocused()
        row(HouseholdWords.MOVE_HERE).assertExists()
        noRow(HouseholdWords.CHANGE_ADMIN)
        noRow(HouseholdWords.USE_HERE)
        noRow(HouseholdWords.DELETE)
    }

    /** A taken name is said on the step that sent it; a free one is sent, and the remote is back on its household. */
    @Test
    fun renameStepsSendTheName() {
        opened()
        repeat(2) { press(Key.DirectionCenter) }
        name("home")
        compose.onNodeWithText(HouseholdWords.HOUSEHOLD_NAME_TAKEN).assertExists()
        name("Loft")
        assertEquals("Loft", core.homes.names["flat"])
        row("Loft · Admin Bea · 2 profiles").assertIsFocused()
    }

    /** A new household run by a new grown-up: its name, the admin, theirs, then their first PIN typed twice. */
    @Test
    fun createStepsSendNameAdminAndPin() {
        opened()
        repeat(2) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        name("Barn")
        row(HouseholdWords.A_NEW_GROWN_UP).assertIsFocused()
        row("Cy").assertExists()
        press(Key.DirectionCenter)
        name("Eve")
        compose.onNodeWithText("A PIN for Eve").assertExists()
        pin("2468")
        pin("2468")

        val eve = core.profiles.single { it.name == "Eve" }
        assertEquals("Barn", core.homes.names[eve.household])
        assertEquals("2468", core.roles.pins[eve.id])
        row(HouseholdWords.CREATE_A_HOUSEHOLD).assertIsFocused()
        row("Barn · Admin Eve · 1 profile").assertExists()
    }

    /** Back walks out the way the remote came in, a row at a time; Back from the list leaves the panel. */
    @Test
    fun backFromAStepReturnsToItsRow() {
        opened()
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        row("Cy").assertIsFocused()

        back()
        row(HouseholdWords.MOVE_HERE).assertIsFocused()
        back()
        row("Home · Admin andre · 1 profile").assertIsFocused()
        assertEquals(0, left)
        back()
        assertEquals(1, left)
    }
}

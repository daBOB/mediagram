package ui.settings

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import kotlinx.coroutines.CompletableDeferred
import model.HouseholdWords
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import ui.profile.COLLAPSED
import ui.profile.tile

/**
 * The owner's household changes on the Profiles page — the web's
 * `household-rows.js`, each behind its household's ⋯ in the owner's view, and
 * the owner's tile for anyone else — over the people [HouseholdsOwnerHarness]
 * sets up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HouseholdsOwnerBlockTest : HouseholdsOwnerHarness() {
    /** Create a household's button, which opens its form; the form's own button says the same, unnamed. */
    private val createOpener = hasContentDescription(HouseholdWords.CREATE_A_HOUSEHOLD)
    private val createSubmit = hasText(HouseholdWords.CREATE_A_HOUSEHOLD) and hasClickAction() and !createOpener

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h2400dp")
    fun theOwnerSeesEveryHouseholdWithItsLine() {
        openAs(owner)
        // Each household heads its part of the owner's view: there is no Households section beside it.
        compose.onNodeWithText(HouseholdWords.HOUSEHOLDS).assertDoesNotExist()
        compose.onNodeWithText(HouseholdWords.OWNER_NOTE).assertDoesNotExist()
        compose.onNodeWithText("No admin · 0 profiles").assertExists()
        compose.onNodeWithText("Admin Dan · 2 profiles").assertExists()
        compose.onNodeWithText("Admin andre · 3 profiles").assertExists()
        val flat = menu("Flat")
        assertTrue(HouseholdWords.CHANGE_ADMIN in flat)
        // The owner runs their own household; this device already shows it.
        val home = menu("Home")
        assertFalse(HouseholdWords.CHANGE_ADMIN in home)
        assertFalse(HouseholdWords.USE_HERE in home)
        assertTrue(HouseholdWords.USE_HERE in flat)
        compose.onNode(createOpener).assertExists()
    }

    // Default window for every PIN: a text-field dialog in a taller one can relayout forever under Robolectric.
    @Test
    fun anyoneElseSeesTheOwnersTileWhichAsksThePin() {
        openAs(bea)
        compose.onNodeWithText(HouseholdWords.WHOS_MANAGING).assertExists()
        compose.onNodeWithText("Admin Dan · 2 profiles").assertDoesNotExist()
        compose.onNode(tile("andre") and hasText(HouseholdWords.OWNER)).performScrollTo().performClick()
        compose.onNode(hasText(HouseholdWords.OWNERS_PIN) and hasAnyAncestor(isDialog())).assertExists()
        pin("1234")
        compose.onNodeWithText(HouseholdWords.WHOS_MANAGING).assertDoesNotExist()
        compose.onNodeWithText("Admin Dan · 2 profiles").assertExists()
    }

    @Test
    fun createWithANewGrownUpSendsItsNameAndPin() {
        openAs(owner)
        compose.onNode(createOpener).performScrollTo().performClick()
        field(HouseholdWords.HOUSEHOLD_NAME).performScrollTo().performTextInput("Loft")
        field(HouseholdWords.NEW_GROWN_UP_NAME).performScrollTo().performTextInput("Eve")
        val create = compose.onNode(createSubmit)
        create.performScrollTo().performClick()
        // The owner's PIN dismissed: nothing sent, nothing typed lost.
        compose.onNode(hasText("Cancel") and hasAnyAncestor(isDialog())).performClick()
        field(HouseholdWords.HOUSEHOLD_NAME).assert(hasText("Loft"))
        create.performScrollTo().performClick()
        pin("1234")
        compose.onNode(hasText("A PIN for Eve") and hasAnyAncestor(isDialog())).assertExists()
        pin("4321")
        pin("4321")

        val loft = core().homes.names.entries.single { it.value == "Loft" }.key
        val eve = core().profiles.single { it.name == "Eve" }
        assertEquals(loft, eve.household)
        assertTrue(eve.admin)
        assertEquals("4321", core().roles.pins[eve.id])
        compose.onNodeWithText("Admin Eve · 1 profile").assertExists()
        // It went through: the form closed.
        field(HouseholdWords.HOUSEHOLD_NAME).assertDoesNotExist()
        compose.onNode(createOpener).assert(hasStateDescription(COLLAPSED))
    }

    @Test
    fun renameChangeAdminMoveAndDeleteEachSendTheHeldPin() {
        openAs(owner)
        choose(HouseholdWords.RENAME, "Flat")
        formField().performScrollTo().performTextReplacement("Attic")
        inForm(HouseholdWords.RENAME).performScrollTo().performClick()
        pin("1234")
        assertEquals("Attic", core().homes.names["flat"])
        formField().assertDoesNotExist()

        // The PIN is held now: nothing else asks for it.
        choose(HouseholdWords.CHANGE_ADMIN, "Attic")
        inForm("Cy").performScrollTo().performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        assertTrue(core().profiles.single { it.id == "c" }.admin)

        choose(HouseholdWords.MOVE_HERE, "Attic")
        inForm("Bea").performScrollTo().performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        assertEquals(listOf("flat", "flat"), core().profiles.filter { it.id in setOf("b", "t") }.map { it.household })

        choose(HouseholdWords.DELETE, "Barn")
        compose.onNode(isDialog()).assertDoesNotExist()
        assertFalse("barn" in core().homes.names)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h2400dp")
    fun deleteOnlyForAnEmptyHouseholdNeverHome() {
        openAs(owner)
        assertTrue(HouseholdWords.DELETE in menu("Barn"))
        assertFalse(HouseholdWords.DELETE in menu("Flat"))
        assertFalse(HouseholdWords.DELETE in menu("Home"))
    }

    /** Use on this device leaves the owner's profile behind in Home, so who is watching is asked again. */
    @Test
    fun useOnThisDeviceAsksWhoIsWatchingThere() {
        openAs(owner)
        choose(HouseholdWords.USE_HERE, "Flat")
        pin("1234")
        assertEquals("flat", core().homes.joined)
        compose.onNodeWithText("Who's watching?").assertExists()
        compose.onNode(hasText("Dan") and hasClickAction()).assertExists()
    }

    @Test
    fun leavingTheSectionDropsTheOwnersPin() {
        openAs(owner)
        choose(HouseholdWords.RENAME, "Barn")
        formField().performScrollTo().performTextReplacement("Shed")
        inForm(HouseholdWords.RENAME).performScrollTo().performClick()
        pin("1234")
        assertEquals("Shed", core().homes.names["barn"])

        compose.runOnUiThread { controller.get().onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("SETTINGS").assertExists()
        // The watching owner's part stays open, so a change is still taken: it asks the PIN again rather than send.
        compose.runOnUiThread { households.rename("barn", "Den") }
        compose.waitForIdle()
        assertEquals(HouseholdWords.OWNERS_PIN, households.pin.value?.title)
        assertEquals("Shed", core().homes.names["barn"], "a change went through on a PIN nobody gave again")
    }

    /** A change out with the owner's PIN: nothing else is pressed until it answers. */
    @Test
    fun nothingIsPressedWhileAChangeIsOut() {
        openAs(owner)
        choose(HouseholdWords.RENAME, "Barn")
        formField().performScrollTo().performTextReplacement("Shed")
        inForm(HouseholdWords.RENAME).performScrollTo().performClick()
        pin("1234")
        val answer = CompletableDeferred<Unit>()
        household.provider.beforeCore = { answer.await() }
        choose(HouseholdWords.DELETE, "Shed")
        more("Flat").assertIsNotEnabled()

        answer.complete(Unit)
        compose.waitForIdle()
        assertFalse("barn" in core().homes.names)
        more("Flat").assertIsEnabled()
    }

    /** As the web says a refusal beside what was refused: under the row it came from, not the part's end. */
    @Test
    fun aRefusalIsSaidBesideTheRowItCameFrom() {
        openAs(owner)
        choose(HouseholdWords.RENAME, "Barn")
        formField().performScrollTo().performTextReplacement("Flat")
        inForm(HouseholdWords.RENAME).performScrollTo().performClick()
        pin("1234")

        assertEquals("Barn", core().homes.names["barn"])
        // Refused: the form stays open with what was typed, as the web's does.
        formField().assert(hasText("Flat"))
        // Unclipped: the default window scrolls, and a row below its fold has no bounds on screen.
        val said = compose.onNodeWithText(HouseholdWords.HOUSEHOLD_NAME_TAKEN).fetchSemanticsNode().positionInRoot.y
        val barn = compose.onNodeWithText("No admin · 0 profiles").fetchSemanticsNode().positionInRoot.y
        val flat = compose.onNodeWithText("Admin Dan · 2 profiles").fetchSemanticsNode().positionInRoot.y
        assertTrue(said in barn..flat, "expected the refusal under Barn, before Flat")
    }
}

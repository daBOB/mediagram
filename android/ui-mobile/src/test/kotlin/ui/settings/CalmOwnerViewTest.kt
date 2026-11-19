package ui.settings

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import catalog.profile.ADD_A_GROWN_UP
import catalog.profile.ADD_A_KID
import catalog.profile.CHANGE_YOUR_PIN
import catalog.profile.RESET_PIN
import model.HouseholdWords
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.profile.COLLAPSED
import ui.profile.EXPANDED
import kotlin.test.assertEquals

/**
 * The owner's calm view — the web's `profile-manage-owner.js` since the user
 * chose it: one row per person, opening on a tap; a household's actions
 * behind its ⋯; one Add someone per household. Over [HouseholdsOwnerHarness]'s
 * Madani: andre, the owner (1234), runs Home with Bea and her kid Tom; Celine
 * runs Madani; Barn is empty. What an add sends is read back from the core.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CalmOwnerViewTest : HouseholdsOwnerHarness() {
    @Test
    fun aPersonIsOneRowUntilTapped() {
        openWithMadani(owner)
        row("Bea").assert(hasText("Grown-up · PIN set")).assert(hasStateDescription(COLLAPSED))
        row("andre").assert(hasText("Owner · Admin · PIN set"))
        // Nothing of anyone's shows until their row is tapped.
        compose.onNode(hasText(RESET_PIN)).assertDoesNotExist()
        compose.onNode(hasText(CHANGE_YOUR_PIN)).assertDoesNotExist()
        compose.onNode(hasContentDescription("Remove Bea")).assertDoesNotExist()
        compose.onNode(hasContentDescription("Sections for Bea")).assertDoesNotExist()
        // Bea's kid is hers to manage: a row with its line, and nothing to open.
        inPart("Home", hasText("Kid · Bea · FSK 12", substring = true)).assert(!hasClickAction())
    }

    @Test
    fun tappingAPersonOpensTheirControlsAndClosesTheOther() {
        openWithMadani(owner)
        row("Bea").performScrollTo().performClick()
        row("Bea").assert(hasStateDescription(EXPANDED))
        compose.onNode(hasText(RESET_PIN) and hasClickAction()).assertExists()
        compose.onNode(hasContentDescription("Remove Bea")).assertExists()
        compose.onNode(hasContentDescription("Sections for Bea")).assertExists()

        row("Celine").performScrollTo().performClick()
        compose.onNode(hasContentDescription("Remove Celine")).assertExists()
        compose.onNode(hasContentDescription("Remove Bea")).assertDoesNotExist()
        row("Bea").assert(hasStateDescription(COLLAPSED))

        // The open one, tapped again, closes.
        row("Celine").performScrollTo().performClick()
        compose.onNode(hasContentDescription("Remove Celine")).assertDoesNotExist()
    }

    @Test
    fun householdMenuHoldsTheAllowedActions() {
        openWithMadani(owner)
        more("Home").assert(hasStateDescription(COLLAPSED))
        with(HouseholdWords) {
            assertEquals(listOf(RENAME, MOVE_HERE), menu("Home"))
            assertEquals(listOf(RENAME, MOVE_HERE, USE_HERE, DELETE), menu("Barn"))
            assertEquals(listOf(RENAME, MOVE_HERE, USE_HERE), menu("Madani"))
        }
        // On the page itself none of them: they wait behind the ⋯.
        compose.onNode(hasText(HouseholdWords.RENAME) and hasClickAction()).assertDoesNotExist()
        // Chosen, one opens its form under its household.
        choose(HouseholdWords.RENAME, "Madani")
        formField().assert(hasText("Madani"))
        assertEquals(1, countIn("Madani", hasText("Cancel") and hasClickAction()))
    }

    @Test
    fun addSomeoneAsksGrownUpOrKid() {
        openWithMadani(owner)
        // One per household, named for it.
        compose.onAllNodes(hasText(HouseholdWords.ADD_SOMEONE) and hasClickAction()).assertCountEquals(3)
        addSomeone("Home").assert(hasStateDescription(COLLAPSED))
        openAdd("Home")
        addSomeone("Home").assert(hasStateDescription(EXPANDED))
        // One form: a grown-up to start with, or a kid.
        inPart("Home", hasText(HouseholdWords.GROWN_UP) and isSelectable()).assertIsSelected()
        inPart("Home", hasText(ADD_A_GROWN_UP) and hasClickAction()).assertExists()
        assertEquals(0, countIn("Home", hasContentDescription(HouseholdWords.BELONGS_TO)))
        inPart("Home", hasText(HouseholdWords.KID) and isSelectable()).performScrollTo().performClick()
        inPart("Home", hasText(ADD_A_KID) and hasClickAction()).assertExists()
        inPart("Home", hasContentDescription("Age limit for the new kid")).assertExists()
        // Home has two grown-ups, so who the kid belongs to is asked.
        assertEquals(1, countIn("Home", hasContentDescription(HouseholdWords.BELONGS_TO)))

        // A household with no grown-up offers only a grown-up.
        openAdd("Barn")
        assertEquals(0, countIn("Barn", hasText(HouseholdWords.KID) and isSelectable()))
        inPart("Barn", hasText(ADD_A_GROWN_UP) and hasClickAction()).assertExists()
    }

    @Test
    fun aRefusedAddKeepsTheOpenForm() {
        openWithMadani(owner)
        add("Barn", addGrownUp, ADD_A_GROWN_UP, "Celine")
        pin("1234")
        pin("4321")
        pin("4321")

        assertEquals(listOf("c"), core().profiles.filter { it.name == "Celine" }.map { it.id })
        // Refused: the form stays open as typed, the refusal under it.
        addSomeone("Barn").assert(hasStateDescription(EXPANDED))
        inPart("Barn", addGrownUp).assert(hasText("Celine"))
        assertEquals(1, countIn("Barn", hasText(HouseholdWords.PROFILE_NAME_TAKEN)))
    }

    @Test
    fun addSomeoneUnderMadaniSendsMadani() {
        openWithMadani(owner)
        add("Madani", addGrownUp, ADD_A_GROWN_UP, "Oma")
        pin("1234")
        compose.onNode(hasText("A PIN for Oma") and hasAnyAncestor(isDialog())).assertExists()
        pin("4321")
        pin("4321")

        assertEquals("madani", made("Oma").household)
        inPart("Madani", hasText("Oma")).assertExists()
        // It went through: the form closed.
        addSomeone("Madani").assert(hasStateDescription(COLLAPSED))
    }
}

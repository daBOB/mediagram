package ui.settings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import catalog.profile.ADD_A_GROWN_UP
import catalog.profile.ADD_A_KID
import catalog.profile.CHANGE_YOUR_PIN
import catalog.profile.EVERYONE_HERE
import catalog.profile.MANAGE_PROFILES
import catalog.profile.SWITCH_PROFILE
import catalog.profile.YOUR_PIN
import model.HouseholdWords
import model.Profile
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.profile.ProfileTileTag
import ui.profile.personRow
import ui.profile.tile
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The owner's one view on the Profiles page — the web's `profile-manage-owner.js`:
 * every household under its heading with its actions behind its ⋯, its people
 * as rows with their lines, Add someone into it, then Create a household.
 * Over [HouseholdsOwnerHarness.openWithMadani]: andre, the owner (PIN 1234),
 * runs Home with Bea and her kid Tom; Celine (5678) runs Madani; Barn is empty.
 * What a form sends is read back from the core: where the new profile landed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OwnerViewTest : HouseholdsOwnerHarness() {
    private val celine = Profile("c", "Celine", admin = true, household = "madani")
    private val kidChoice = hasText(HouseholdWords.KID) and isSelectable()

    /** The households' headings, top to bottom. */
    private fun headings(): List<String> =
        compose.onAllNodes(isHeading()).fetchSemanticsNodes().sortedBy { it.positionInRoot.y }
            .mapNotNull { node -> node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } }
            .filter { it in setOf("Home", "Barn", "Madani") }

    @Test
    fun ownerSeesEveryHouseholdWithItsActions() {
        openWithMadani(owner)
        compose.onNodeWithText("As andre").assertExists()
        compose.onNodeWithText(HouseholdWords.WHOS_MANAGING).assertDoesNotExist()
        // This device's household first, then the others in the live order.
        assertEquals(listOf("Home", "Barn", "Madani"), headings())
        inPart("Home", hasText("Admin andre · 3 profiles")).assertExists()
        inPart("Barn", hasText("No admin · 0 profiles")).assertExists()
        inPart("Madani", hasText("Admin Celine · 1 profile")).assertExists()
        listOf("Home", "Barn", "Madani").forEach { assertTrue(HouseholdWords.RENAME in menu(it), "no Rename for $it") }
        val home = menu("Home")
        assertFalse(HouseholdWords.CHANGE_ADMIN in home)
        assertFalse(HouseholdWords.USE_HERE in home)
        assertTrue(HouseholdWords.USE_HERE in menu("Madani"))
        assertTrue(HouseholdWords.DELETE in menu("Barn"))
        assertFalse(HouseholdWords.DELETE in menu("Madani"))
        // Each person with their line; the owner's own PIN on their row.
        inPart("Home", hasText("Owner · Admin · PIN set")).assertExists()
        opened("andre")
        inPart("Home", hasText(CHANGE_YOUR_PIN) and hasClickAction()).assertExists()
        inPart("Home", hasText("Grown-up · PIN set")).assertExists()
        opened("Bea")
        inPart("Home", hasContentDescription("Remove Bea")).assertExists()
        inPart("Madani", hasText("Admin · PIN set")).assertExists()
        opened("Celine")
        inPart("Madani", hasContentDescription("Remove Celine")).assertExists()
        // Bea's kid is hers to manage: listed, with nothing to open or change.
        inPart("Home", hasText("Kid · Bea · FSK 12", substring = true)).assertExists()
        compose.onNode(personRow("Tom")).assertDoesNotExist()
        compose.onNode(hasContentDescription("Remove Tom")).assertDoesNotExist()
        compose.onNode(hasContentDescription("Age limit for Tom")).assertDoesNotExist()
        compose.onNodeWithText(YOUR_PIN).assertDoesNotExist()
        // Create a household after the last household, This device after it.
        val create = ys(hasContentDescription(HouseholdWords.CREATE_A_HOUSEHOLD)).single()
        assertTrue(create > ys(hasText("Madani") and isHeading()).single(), "expected Create a household after Madani")
        assertTrue(ys(hasText(HouseholdWords.THIS_DEVICE)).single() > create, "expected This device after Create a household")
    }

    @Test
    fun addGrownUpUnderMadaniSendsMadani() {
        openWithMadani(owner)
        add("Madani", addGrownUp, ADD_A_GROWN_UP, "Oma")
        pin("1234")
        compose.onNode(hasText("A PIN for Oma") and hasAnyAncestor(isDialog())).assertExists()
        pin("4321")
        pin("4321")

        assertEquals("madani", made("Oma").household)
        inPart("Madani", hasText("Oma")).assertExists()
    }

    @Test
    fun kidFormAsksBelongsToOnlyWithTwoGrownUps() {
        openWithMadani(owner)
        openAdd("Home")
        inPart("Home", kidChoice).performScrollTo().performClick()
        openAdd("Madani")
        inPart("Madani", kidChoice).performScrollTo().performClick()
        assertEquals(1, countIn("Home", hasContentDescription(HouseholdWords.BELONGS_TO)))
        assertEquals(0, countIn("Madani", hasContentDescription(HouseholdWords.BELONGS_TO)))
        // The household's admin to start with; another of its grown-ups when chosen.
        inPart("Home", hasText("andre") and isSelectable()).assertIsSelected()
        inPart("Home", hasText("Bea") and isSelectable()).performScrollTo().performClick()
        add("Home", addKid, ADD_A_KID, "Mia")
        pin("1234")
        assertEquals("b" to "home", made("Mia").parentId to made("Mia").household)

        // With one grown-up there is nothing to ask: the kid is theirs.
        add("Madani", addKid, ADD_A_KID, "Kai")
        assertEquals("c" to "madani", made("Kai").parentId to made("Kai").household)
    }

    @Test
    fun emptyHouseholdHasNoAddKid() {
        openWithMadani(owner)
        listOf("Barn", "Madani", "Home").forEach(::openAdd)
        assertEquals(1, countIn("Barn", addGrownUp))
        assertEquals(0, countIn("Barn", kidChoice))
        assertEquals(1, countIn("Madani", kidChoice))
        assertEquals(1, countIn("Home", kidChoice))
    }

    @Test
    fun everyoneHereAndOwnerButtonHiddenInOwnerView() {
        openWithMadani(owner)
        compose.onNodeWithText(EVERYONE_HERE).assertDoesNotExist()
        compose.onNodeWithText(HouseholdWords.HOUSEHOLDS).assertDoesNotExist()
        compose.onNodeWithText(HouseholdWords.OWNER_NOTE).assertDoesNotExist()
        compose.onNode(hasText(HouseholdWords.OWNER) and hasClickAction()).assertDoesNotExist()

        // Over the picker nobody is chosen: Who's managing?, and no Everyone here or Owner button, until the owner's tile and PIN.
        compose.onNode(hasText(SWITCH_PROFILE) and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText(MANAGE_PROFILES).performClick()
        compose.onNodeWithText(HouseholdWords.WHOS_MANAGING).assertExists()
        compose.onNodeWithText(EVERYONE_HERE).assertDoesNotExist()
        compose.onNode(hasText(HouseholdWords.OWNER) and hasClickAction() and !hasTestTag(ProfileTileTag)).assertDoesNotExist()
        compose.onNode(tile("andre")).performScrollTo().performClick()
        pin("1234")
        compose.onNodeWithText("As andre").assertExists()
        compose.onNodeWithText(EVERYONE_HERE).assertDoesNotExist()
        compose.onNodeWithText(HouseholdWords.OWNER_NOTE).assertDoesNotExist()
        assertEquals(listOf("Home", "Barn", "Madani"), headings())
    }

    @Test
    fun ownersTileOnAnotherHouseholdsDeviceOpensOwnerView() {
        openWithMadani(celine, joined = "madani")
        compose.onNodeWithText(HouseholdWords.WHOS_MANAGING).assertExists()
        compose.onNodeWithText(EVERYONE_HERE).assertDoesNotExist()
        // The owner is not among those this device shows: their tile, labelled Owner, is the way in.
        compose.onNode(tile("andre") and hasText(HouseholdWords.OWNER)).performScrollTo().performClick()
        pin("1234")

        compose.onNodeWithText("As andre").assertExists()
        compose.onNodeWithText(EVERYONE_HERE).assertDoesNotExist()
        compose.onNodeWithText(HouseholdWords.OWNER_NOTE).assertDoesNotExist()
        assertEquals(listOf("Madani", "Barn", "Home"), headings())
        // The PIN given to the tile is the view's: a change asks nothing more.
        add("Madani", addKid, ADD_A_KID, "Kai")
        compose.onNode(isDialog()).assertDoesNotExist()
        assertEquals("madani", made("Kai").household)
    }

    @Test
    fun takenNameIsSaidUnderThatHouseholdsForm() {
        openWithMadani(owner)
        add("Barn", addGrownUp, ADD_A_GROWN_UP, "Celine")
        pin("1234")
        pin("4321")
        pin("4321")

        assertEquals(listOf("c"), core().profiles.filter { it.name == "Celine" }.map { it.id })
        compose.onAllNodes(hasText(HouseholdWords.PROFILE_NAME_TAKEN)).assertCountEquals(1)
        assertEquals(1, countIn("Barn", hasText(HouseholdWords.PROFILE_NAME_TAKEN)))
    }
}

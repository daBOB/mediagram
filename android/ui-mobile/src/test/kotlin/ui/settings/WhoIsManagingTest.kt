package ui.settings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import catalog.profile.EVERYONE_HERE
import catalog.profile.MANAGE_PROFILES
import catalog.profile.SWITCH_PROFILE
import model.HouseholdWords
import model.Profile
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.profile.ProfileTileTag
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Who's managing? — the web's `profile-manage-who.js`: Manage profiles' one
 * question, asked with Who's watching?'s own tiles, over the people
 * [HouseholdsOwnerHarness] sets up: andre, the owner (PIN 1234), runs Home
 * with Bea and her kid Tom; Dan runs Flat with Cy; Barn is empty.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WhoIsManagingTest : HouseholdsOwnerHarness() {
    private val dan = Profile("d", "Dan", admin = true, household = "flat")

    /** The tiles' names as they read: row by row, left to right. */
    private fun tiles(): List<String> =
        compose.onAllNodes(hasTestTag(ProfileTileTag)).fetchSemanticsNodes()
            .sortedWith(compareBy({ it.positionInRoot.y }, { it.positionInRoot.x }))
            .map { it.config[SemanticsProperties.Text].first().text }

    private fun tile(name: String) = compose.onNode(hasTestTag(ProfileTileTag) and hasText(name))

    private fun headingY(name: String) = compose.onNode(hasText(name) and isHeading()).fetchSemanticsNode().positionInRoot.y

    @Test
    fun managingAsksWhoWithThePickersTiles() {
        // Bea listed before the owner: the owner's tile comes first all the same.
        openAs(owner, people = listOf(tom, bea, owner))
        compose.onNode(hasText(SWITCH_PROFILE) and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText(MANAGE_PROFILES).performClick()

        compose.onNode(hasText(HouseholdWords.WHOS_MANAGING) and isHeading()).assertExists()
        // The grown-ups here; a kid manages nothing and has no tile.
        assertEquals(listOf("andre", "Bea"), tiles())
        // One question: no Who are you?, Everyone here, Households gate or This device beside it.
        compose.onNodeWithText("Who are you?").assertDoesNotExist()
        compose.onNodeWithText(EVERYONE_HERE).assertDoesNotExist()
        compose.onNodeWithText(HouseholdWords.OWNER_NOTE).assertDoesNotExist()
        compose.onNodeWithText(HouseholdWords.THIS_DEVICE).assertDoesNotExist()
    }

    // Default window for every PIN: a text-field dialog in a taller one can relayout forever under Robolectric.
    @Test
    fun ownersTileSaysOwnerAndOpensTheOwnerView() {
        openAs(bea)
        compose.onNode(hasText(HouseholdWords.WHOS_MANAGING) and isHeading()).assertExists()
        tile("andre").assert(hasText(HouseholdWords.OWNER))
        tile("Bea").assert(!hasText(HouseholdWords.OWNER))

        tile("andre").performScrollTo().performClick()
        compose.onNode(hasText(HouseholdWords.OWNERS_PIN) and hasAnyAncestor(isDialog())).assertExists()
        pin("1234")

        compose.onNodeWithText("As andre").assertExists()
        compose.onNodeWithText("Admin Dan · 2 profiles").assertExists()
        compose.onAllNodes(hasTestTag(ProfileTileTag)).assertCountEquals(0)
    }

    @Test
    fun anotherHouseholdsDeviceEndsWithAnOwnerTile() {
        openAs(dan, joined = "flat")
        // The owner is not among those this device shows: their tile ends the row.
        assertEquals(listOf("Dan", "Cy", "andre"), tiles())
        tile("andre").assert(hasText(HouseholdWords.OWNER))

        tile("andre").performScrollTo().performClick()
        compose.onNode(hasText(HouseholdWords.OWNERS_PIN) and hasAnyAncestor(isDialog())).assertExists()
        pin("1234")

        compose.onNodeWithText("As andre").assertExists()
        // This device's household first.
        assertTrue(headingY("Flat") < headingY("Home"), "expected Flat, this device's, before Home")
    }
}

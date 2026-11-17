package ui.profile

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import catalog.profile.ADD_A_GROWN_UP
import catalog.profile.ADD_A_KID
import catalog.profile.ManageUiState
import catalog.profile.RESET_PIN
import model.Household
import model.HouseholdWords
import model.Profile
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * The owner's Manage profiles on the phone — the web's `profile-manage-owner.test.ts`:
 * andre owns the install and runs Home with Lea; Maja runs the Flat with Tim;
 * Ida runs the Loft, which the live order lists first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp")
class ManageProfilesHouseholdsTest : ManageScreenHarness() {
    private val andre = Profile("andre", "andre", admin = true, hasPin = true, owner = true)
    private val lea = Profile("lea", "Lea", kids = true, kidsAge = 12)
    private val maja = Profile("maja", "Maja", admin = true, hasPin = true, household = "flat")
    private val tim = Profile("tim", "Tim", kids = true, kidsAge = 6, parentId = "maja", household = "flat")
    private val ida = Profile("ida", "Ida", admin = true, hasPin = true, household = "loft")
    private val live = listOf(Household("loft", "Loft", "ida"), Household("flat", "Flat", "maja"), Household("home", "Home", "andre"))

    /** The headings and add forms, top to bottom, with the households' [names]. */
    private fun drawn(vararg names: String): List<String> {
        val kept = setOf(*names, "Grown-ups", "Kids", ADD_A_GROWN_UP, ADD_A_KID, "Your PIN", HouseholdWords.ADD_SOMEONE)
        fun SemanticsNode.texts(): List<String> = config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + children.flatMap { it.texts() }
        return compose.onRoot(useUnmergedTree = true).fetchSemanticsNode().texts().filter { it in kept }
    }

    // The web's owner view: people straight under their household, no Grown-ups or Kids, one Add someone each.
    @Test fun theOwnerSeesEveryHouseholdUnderItsNameThisDevicesFirstAndAddsInEach() {
        show(ManageUiState.Managing(andre, grownUps = listOf(maja, ida), kids = listOf(lea, tim), households = live))
        val add = HouseholdWords.ADD_SOMEONE
        assertEquals(listOf("Home", add, "Loft", add, "Flat", add), drawn("Home", "Loft", "Flat"))
    }

    // A household the list does not name yet keeps its people, unheaded: the web draws no heading for it.
    @Test fun aHouseholdTheListDoesNotNameYetHasNoHeading() {
        val ned = Profile("ned", "Ned", admin = true, hasPin = true, household = "barn")
        show(ManageUiState.Managing(andre, grownUps = listOf(maja, ida, ned), kids = listOf(lea, tim), households = live))
        val add = HouseholdWords.ADD_SOMEONE
        assertEquals(listOf("Home", add, "Loft", add, "Flat", add, add), drawn("Home", "Loft", "Flat", "barn"))
        compose.onNode(personRow("Ned")).assertExists()
    }

    // Someone the owner may not change is one thing said, their name with their line, and no button.
    @Test fun aReadOnlyRowIsReadAsOne() {
        val test = Profile("test", "test", hasPin = true)
        val kim = Profile("kim", "Kim", kids = true, kidsAge = 6, parentId = "test")
        show(ManageUiState.Managing(andre, listOf(test), listOf(lea), households = live, everyone = listOf(andre, test, lea, kim)))
        compose.onNode(hasText("Kim") and hasText("Kid · test · FSK 6") and !hasClickAction()).assertExists()
    }

    @Test fun anotherHouseholdsRowsKeepTheirActions() {
        show(ManageUiState.Managing(andre, grownUps = listOf(ida), kids = listOf(tim), households = live))
        compose.onNode(personRow("Ida")).performClick()
        compose.onNode(hasText(RESET_PIN)).performClick()
        compose.onNode(personRow("Tim")).performClick()
        compose.onNode(hasText("FSK 12") and hasAnyAncestor(hasContentDescription("Age limit for Tim"))).performClick()
        compose.onNode(personRow("Ida")).performClick()
        compose.onNode(hasText("Anime") and hasAnyAncestor(hasContentDescription("Sections for Ida"))).performClick()
        compose.onNode(hasContentDescription("Remove Ida")).performClick()
        compose.onNode(hasText("Remove") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(listOf("pin ida", "age tim 12", "sections ida [anime]", "remove ida"), calls)
    }

    @Test fun anyoneElseSeesTheirOwnRowsUnnamedThenAddSomeone() {
        show(ManageUiState.Managing(maja, grownUps = emptyList(), kids = listOf(tim), households = live))
        // Themselves, then their kid, then Add someone: no household named, no Grown-ups, Kids or Your PIN.
        assertEquals(listOf(HouseholdWords.ADD_SOMEONE), drawn("Home", "Loft", "Flat"))
        compose.onNode(personRow("Maja")).assertExists()
        compose.onNode(personRow("Tim")).assertExists()
    }
}

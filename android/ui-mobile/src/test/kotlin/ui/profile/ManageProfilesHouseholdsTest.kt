package ui.profile

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import catalog.profile.ADD_A_GROWN_UP
import catalog.profile.ADD_A_KID
import catalog.profile.GROWN_UPS
import catalog.profile.KIDS_SECTION
import catalog.profile.ManageUiState
import catalog.profile.RESET_PIN
import catalog.profile.YOUR_PIN
import model.Household
import model.Profile
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.common.profile.ManageActions
import kotlin.test.assertEquals

/**
 * The owner's Manage profiles on the phone — the web's `profile-manage-owner.test.ts`:
 * andre owns the install and runs Home with Lea; Maja runs the Flat with Tim;
 * Ida runs the Loft, which the live order lists first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp")
class ManageProfilesHouseholdsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    private val andre = Profile("andre", "andre", admin = true, hasPin = true, owner = true)
    private val lea = Profile("lea", "Lea", kids = true, kidsAge = 12)
    private val maja = Profile("maja", "Maja", admin = true, hasPin = true, household = "flat")
    private val tim = Profile("tim", "Tim", kids = true, kidsAge = 6, parentId = "maja", household = "flat")
    private val ida = Profile("ida", "Ida", admin = true, hasPin = true, household = "loft")
    private val live = listOf(Household("loft", "Loft", "ida"), Household("flat", "Flat", "maja"), Household("home", "Home", "andre"))
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

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(state: ManageUiState) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { MaterialTheme { ManageProfilesScreen(state, actions) } }
        }
        compose.waitForIdle()
    }

    /** The headings and add forms, top to bottom, with the households' [names]. */
    private fun drawn(vararg names: String): List<String> {
        val kept = setOf(*names, GROWN_UPS, KIDS_SECTION, ADD_A_GROWN_UP, ADD_A_KID, YOUR_PIN)
        fun SemanticsNode.texts(): List<String> = config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + children.flatMap { it.texts() }
        return compose.onRoot(useUnmergedTree = true).fetchSemanticsNode().texts().filter { it in kept }
    }

    @Test fun theOwnerSeesEveryHouseholdUnderItsNameThisDevicesFirstAndAddsOnlyThere() {
        show(ManageUiState.Managing(andre, grownUps = listOf(maja, ida), kids = listOf(lea, tim), households = live))
        assertEquals(
            listOf("Home", GROWN_UPS, ADD_A_GROWN_UP, KIDS_SECTION, ADD_A_KID, "Loft", GROWN_UPS, "Flat", GROWN_UPS, KIDS_SECTION, YOUR_PIN),
            drawn("Home", "Loft", "Flat"),
        )
    }

    @Test fun anotherHouseholdsRowsKeepTheirActions() {
        show(ManageUiState.Managing(andre, grownUps = listOf(ida), kids = listOf(tim), households = live))
        compose.onNode(hasText(RESET_PIN)).performClick()
        compose.onNode(hasText("FSK 12") and hasAnyAncestor(hasContentDescription("Age limit for Tim"))).performClick()
        compose.onNode(hasText("Anime") and hasAnyAncestor(hasContentDescription("Sections for Ida"))).performClick()
        compose.onNode(hasContentDescription("Remove Ida")).performClick()
        compose.onNode(hasText("Remove") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(listOf("pin ida", "age tim 12", "sections ida [anime]", "remove ida"), calls)
    }

    @Test fun anyoneElseSeesThisHouseholdUnnamedAsBefore() {
        show(ManageUiState.Managing(maja, grownUps = emptyList(), kids = listOf(tim), households = live))
        assertEquals(listOf(GROWN_UPS, ADD_A_GROWN_UP, KIDS_SECTION, ADD_A_KID, YOUR_PIN), drawn("Home", "Loft", "Flat"))
    }
}

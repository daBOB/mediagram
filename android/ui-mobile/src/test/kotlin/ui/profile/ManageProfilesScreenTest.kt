package ui.profile

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import catalog.profile.ManageUiState
import model.Profile
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** Manage profiles drawn from its state, section for section as `profile-manage.js` draws it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp")
class ManageProfilesScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    private val andre = Profile("a", "andre", admin = true, hasPin = true)
    private val bea = Profile("b", "Bea", hasPin = true)
    private val tom = Profile("t", "Tom", kids = true, kidsAge = 12, parentId = "b")
    private val calls = mutableListOf<String>()
    private val actions =
        ManageActions(
            onActAs = { calls += "actAs $it" },
            onAddKid = { name, age -> calls += "addKid $name $age" },
            onSetKidsAge = { id, age -> calls += "age $id $age" },
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

    private fun inDialog(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    @Test fun whoAreYouOffersTheGrownUpsAndSaysWhyItAsksAgain() {
        show(ManageUiState.ChoosingActor(listOf(andre, bea), notice = "Your PIN is no longer valid. Choose who you are again."))
        compose.onNodeWithText("Manage profiles").assertExists()
        compose.onNodeWithText("Who are you?").assertExists()
        compose.onNodeWithText("Your PIN is no longer valid. Choose who you are again.").assertExists()
        compose.onNodeWithText("Bea").performClick()
        compose.onNodeWithText("Done").performClick()
        assertEquals(listOf("actAs b", "close"), calls)
    }

    @Test fun aParentSeesItsKidsWithTheirLimitAndNoGrownUps() {
        show(ManageUiState.Managing(bea, grownUps = emptyList(), kids = listOf(tom)))
        compose.onNodeWithText("As Bea").assertExists()
        compose.onNodeWithText("Grown-ups").assertDoesNotExist()
        compose.onNodeWithText("Add a grown-up").assertDoesNotExist()
        compose.onNode(hasText("FSK 12") and hasAnyAncestor(hasContentDescription("Age limit for Tom"))).assertIsSelected()
        compose.onNode(hasText("FSK 6") and hasAnyAncestor(hasContentDescription("Age limit for Tom"))).performClick()
        assertEquals(listOf("age t 6"), calls)
    }

    @Test fun theAdminResetsAndRemovesGrownUpsAndIsAskedFirst() {
        show(ManageUiState.Managing(andre, grownUps = listOf(bea), kids = emptyList()))
        compose.onNodeWithText("Grown-ups").assertExists()
        compose.onNodeWithText("Reset PIN").performClick()
        compose.onNode(hasText("Remove") and hasContentDescription("Remove Bea")).performClick()
        inDialog("Remove Bea, their kids, and everything they have watched?").assertExists()
        inDialog("Remove").performClick()
        assertEquals(listOf("pin b", "remove b"), calls)
    }

    @Test fun cancellingTheQuestionRemovesNobody() {
        show(ManageUiState.Managing(bea, grownUps = emptyList(), kids = listOf(tom)))
        compose.onNode(hasContentDescription("Remove Tom")).performClick()
        inDialog("Remove Tom and everything they have watched?").assertExists()
        inDialog("Cancel").performClick()
        assertEquals(emptyList(), calls)
    }

    @Test fun aNewKidNeedsANameAndStartsAtSix() {
        show(ManageUiState.Managing(bea, emptyList(), emptyList()))
        compose.onNodeWithText("Add a kid").assertIsNotEnabled()
        compose.onNode(hasSetTextAction() and hasContentDescription("Add a kid: name")).performTextInput("Lina")
        compose.onNodeWithText("Add a kid").performClick()
        compose.onNode(hasSetTextAction() and hasContentDescription("Add a kid: name")).performTextInput("Ole")
        compose.onNode(hasText("FSK 12") and hasAnyAncestor(hasContentDescription("Age limit for the new kid"))).performClick()
        compose.onNodeWithText("Add a kid").performClick()
        assertEquals(listOf("addKid Lina 6", "addKid Ole 12"), calls)
    }

    @Test fun theAdminAddsAGrownUpByName() {
        show(ManageUiState.Managing(andre, emptyList(), emptyList()))
        compose.onNode(hasSetTextAction() and hasContentDescription("Add a grown-up: name")).performTextInput("Carl")
        compose.onNodeWithText("Add a grown-up").performClick()
        assertEquals(listOf("addGrownUp Carl"), calls)
    }

    @Test fun whyTheLastChangeFailedIsSaidAndOwnPinIsChangedHere() {
        show(ManageUiState.Managing(bea, emptyList(), emptyList(), notice = "A profile with that name already exists."))
        compose.onNodeWithText("A profile with that name already exists.").assertExists()
        compose.onNodeWithText("Your PIN").assertExists()
        compose.onNodeWithText("Change your PIN").performClick()
        compose.onNodeWithText("Done").performClick()
        assertEquals(listOf("pin b", "close"), calls)
    }
}

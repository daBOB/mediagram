package ui.profile

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import catalog.profile.ProfileUiState
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
import kotlin.test.assertTrue

/** The picker's three start states as the web draws them — `profile-picker.js`'s `draw`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h900dp")
class ProfilePickerScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    private val calls = mutableListOf<String>()
    private val actions =
        PickerActions(
            onPick = { calls += "pick $it" },
            onClaim = { calls += "claim $it" },
            onCreateFirst = { calls += "first $it" },
            onManage = { calls += "manage" },
            onStay = { calls += "stay" },
            onRetry = { calls += "retry" },
        )

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(state: ProfileUiState) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { MaterialTheme { ProfilePickerScreen(state, actions) } }
        }
        compose.waitForIdle()
    }

    @Test fun aKidsTileNamesItsOwnLimitAndATapPicksIt() {
        show(ProfileUiState.Picking(listOf(Profile("a", "andre", admin = true), Profile("m", "Mia", kids = true, kidsAge = 6)), canStay = false))
        compose.onNodeWithText("Kids · FSK 6").assertExists()
        compose.onNodeWithText("KIDS").assertDoesNotExist()
        compose.onNodeWithText("Mia").performClick()
        compose.onNodeWithText("andre").performClick()
        assertEquals(listOf("pick m", "pick a"), calls)
    }

    @Test fun aDeviceWithNoGrownUpMakesTheFirstAndStillShowsItsKids() {
        show(ProfileUiState.Picking(listOf(Profile("k", "TV kids", kids = true)), canStay = false))
        compose.onNodeWithText("Create the first profile — it runs this household").assertExists()
        compose.onNodeWithText("Kids · FSK 12").assertExists()
        compose.onNodeWithText("Manage profiles").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextInput("  Ann ")
        compose.onNodeWithText("Create").performClick()
        assertEquals(listOf("first Ann"), calls)
    }

    @Test fun aDeviceThatKnowsNobodyYetCanLookAgainForTheHouseholdsSync() {
        show(ProfileUiState.Picking(emptyList(), canStay = false))
        compose.onNodeWithText("Create the first profile — it runs this household").assertExists()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(listOf("retry"), calls)
    }

    @Test fun grownUpsWithNoAdminAreAskedWhoRunsTheHousehold() {
        show(ProfileUiState.Picking(listOf(Profile("a", "andre"), Profile("m", "Mia", kids = true)), canStay = false))
        compose.onNodeWithText("Who runs this household?").assertExists()
        compose.onNodeWithText("Create the first profile — it runs this household").assertDoesNotExist()
        compose.onNode(hasText("Mia") and hasAnyAncestor(hasTestTag(WhoRunsTag))).assertDoesNotExist()
        compose.onNode(hasText("andre") and hasAnyAncestor(hasTestTag(WhoRunsTag))).performClick()
        compose.onNodeWithText("Manage profiles").performClick()
        assertEquals(listOf("claim a", "manage"), calls)
    }

    @Test fun anAdminHouseholdOffersManageAndSaysThePinIsNotALogin() {
        show(ProfileUiState.Picking(listOf(Profile("a", "andre", admin = true)), canStay = true))
        compose.onNodeWithText("Who runs this household?").assertDoesNotExist()
        compose.onNodeWithText("it is not a login", substring = true).assertExists()
        compose.onNodeWithText("Manage profiles").performClick()
        compose.onNodeWithText("Stay as I am").performClick()
        assertEquals(listOf("manage", "stay"), calls)
    }

    @Test fun aRefusalIsSaidAboveTheTiles() {
        show(ProfileUiState.Picking(listOf(Profile("a", "andre", admin = true)), canStay = false, notice = "That is not allowed."))
        val notice = compose.onNodeWithText("That is not allowed.").fetchSemanticsNode().boundsInRoot
        val tile = compose.onNodeWithText("andre").fetchSemanticsNode().boundsInRoot
        assertTrue(notice.bottom <= tile.top, "the notice ends at ${notice.bottom}, the tiles start at ${tile.top}")
    }

    @Test fun aLoadThatFailedAsksToTryAgainNotToStartAHousehold() {
        show(ProfileUiState.Picking(emptyList(), canStay = false, error = "Could not load profiles. Please try again."))
        compose.onNodeWithText("Create the first profile — it runs this household").assertDoesNotExist()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(listOf("retry"), calls)
    }
}

package ui.tv.profile

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
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
import ui.tv.TvTheme

/**
 * What Robolectric can check about [TvProfilePicker] without a real window
 * manager: which state draws what text, and that clicking through to Add
 * lands on the right screens — the same appearance-only split every other
 * TV screen in this module follows (`TvSetupStepTest`, `TvConfirmDialogStateTest`).
 * Initial focus and D-pad traversal are real window-manager behaviour and
 * live in `ui-tv/src/androidTest/kotlin/ui/tv/profile/TvProfilePickerTest.kt`
 * instead.
 */
// A television-sized window, not Robolectric's own narrow default: the tile
// row is a LazyRow now (see TvProfilePicker), which only composes semantics
// nodes for tiles that actually land inside the measured viewport — a
// narrower window would drop the trailing "New profile" tile from the tree
// these tests query, not just fail to show it.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp")
class TvProfilePickerStateTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    @Test
    fun chosenRendersNothing() {
        show(ProfileUiState.Chosen(Profile(id = "ada", name = "Ada")))
        compose.onNodeWithText("Who's watching?").assertDoesNotExist()
    }

    @Test
    fun pickingShowsTheHeadingAndEveryProfilesName() {
        show(
            ProfileUiState.Picking(
                profiles = listOf(Profile(id = "ada", name = "Ada"), Profile(id = "bea", name = "Bea", kids = true)),
                canStay = false,
            ),
        )
        compose.onNodeWithText("Who's watching?").assertExists()
        compose.onNodeWithText("Ada").assertExists()
        compose.onNodeWithText("Bea").assertExists()
        compose.onNodeWithText("New profile").assertExists()
    }

    /** The phone's own tile label, unabridged — see `ui.profile.ProfilePickerScreen.ProfileTile`. */
    @Test
    fun aKidsProfilesTileCarriesTheKidsLabelAndAPlainOneDoesNot() {
        show(
            ProfileUiState.Picking(
                profiles = listOf(Profile(id = "ada", name = "Ada"), Profile(id = "bea", name = "Bea", kids = true)),
                canStay = false,
            ),
        )
        compose.onNodeWithText("KIDS").assertExists()
    }

    @Test
    fun anErrorShowsAboveTheTilesWithATryAgainRow() {
        show(ProfileUiState.Picking(profiles = emptyList(), canStay = false, error = "Could not load profiles. Please try again."))
        compose.onNodeWithText("Could not load profiles. Please try again.").assertExists()
        compose.onNodeWithText("Try again").assertExists()
    }

    @Test
    fun canStayOffersNoStayRowAndCanStayOnDoes() {
        show(ProfileUiState.Picking(profiles = emptyList(), canStay = false))
        compose.onNodeWithText("Stay as I am").assertDoesNotExist()

        show(ProfileUiState.Picking(profiles = emptyList(), canStay = true))
        compose.onNodeWithText("Stay as I am").assertExists()
    }

    @Test
    fun theAddTileOpensTheNameQuestion() {
        show(ProfileUiState.Picking(profiles = listOf(Profile(id = "ada", name = "Ada")), canStay = false))

        compose.onNodeWithText("New profile").performSemanticsAction(SemanticsActions.OnClick)

        compose.onNodeWithText("Name for this profile").assertExists()
        compose.onNodeWithText("Name").assertExists()
    }

    private fun show(state: ProfileUiState) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                TvTheme {
                    TvProfilePicker(state = state, onChoose = {}, onAdd = { _, _ -> }, onStay = {}, onRetry = {})
                }
            }
        }
        compose.waitForIdle()
    }
}

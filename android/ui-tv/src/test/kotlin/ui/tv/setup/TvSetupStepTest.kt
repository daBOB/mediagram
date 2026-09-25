package ui.tv.setup

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import setup.API_HASH_ERROR
import setup.LibraryOption
import setup.SetupUiState
import ui.tv.TvAppFixture
import ui.tv.TvSetupStage
import ui.tv.TvTheme

/**
 * What Robolectric can check about [TvSetupStep] without a real window
 * manager: which screen a given [SetupUiState] draws, what it says, and
 * whether Start over is offered — the same appearance-only split every
 * other TV screen in this module follows (`TvTextQuestionStateTest`,
 * `TvConfirmDialogStateTest`). Focus and D-pad traversal are real
 * window-manager behaviour and live in the androidTest set instead.
 *
 * A real `setup.SetupViewModel` backs every case, built the same way
 * `TvAppTest` builds one — but its own derived state is never read here.
 * [TvSetupStep] is driven by whatever [SetupUiState] a test hands it
 * directly, so a case can exercise a branch the fixture's own storage/core
 * path could not naturally reach, `Failed` above all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TvSetupStepTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    private lateinit var fixture: TvAppFixture

    @After
    fun close() {
        compose.runOnUiThread {
            if (::controller.isInitialized) controller.close()
            if (::fixture.isInitialized) fixture.close()
        }
    }

    @Test
    fun checkingOffersNoStartOver() {
        show(SetupUiState.Checking)
        compose.onNodeWithText("Start over").assertDoesNotExist()
    }

    @Test
    fun needsApplicationShowsTheSameHeadingAndExplanationAsThePhone() {
        show(SetupUiState.NeedsApplication())
        // Exact matches, not substrings: the heading and the field label
        // are their own nodes now, not folded into the explanation's text —
        // the collapse a joined "prompt" string once produced.
        compose.onNodeWithText("Connect this device to Telegram").assertExists()
        compose.onNodeWithText("my.telegram.org", substring = true).assertExists()
        compose.onNodeWithText("api_id").assertExists()
    }

    @Test
    fun needsApplicationOffersNoStartOverSinceItIsTheFirstStep() {
        show(SetupUiState.NeedsApplication())
        compose.onNodeWithText("Start over").assertDoesNotExist()
    }

    @Test
    fun needsApplicationShowsAnErrorAboveTheField() {
        show(SetupUiState.NeedsApplication(error = "The api_id is the number shown next to your application."))
        compose.onNodeWithText("The api_id is the number shown next to your application.", substring = true).assertExists()
    }

    /**
     * A hash rejection used to send the remote back to api_id regardless —
     * the api_hash error text shown on the api_id screen, which reads as
     * nothing having happened when what was actually typed was the hash.
     * [API_HASH_ERROR] now keeps the viewer on api_hash instead.
     */
    @Test
    fun needsApplicationWithAHashErrorShowsItOnTheHashStep() {
        show(SetupUiState.NeedsApplication(error = API_HASH_ERROR))
        compose.onNodeWithText("api_hash").assertExists()
        compose.onNodeWithText(API_HASH_ERROR, substring = true).assertExists()
        compose.onNodeWithText("api_id").assertDoesNotExist()
    }

    @Test
    fun needsLibraryWithNothingFetchedYetShowsNoHeadingYet() {
        show(SetupUiState.NeedsLibrary())
        compose.onNodeWithText("Which library should this device read?").assertDoesNotExist()
    }

    @Test
    fun needsLibraryWithAnErrorAndNoChoicesOffersLookAgain() {
        show(SetupUiState.NeedsLibrary(error = "Could not reach Telegram."))
        compose.onNodeWithText("Look again").assertExists()
        compose.onNodeWithText("Could not reach Telegram.", substring = true).assertExists()
    }

    @Test
    fun needsLibraryShowsTheHeadingAndTheFetchedChoices() {
        show(SetupUiState.NeedsLibrary(choices = listOf(LibraryOption("films", "Family films"))))
        compose.onNodeWithText("Which library should this device read?").assertExists()
        compose.onNodeWithText("Family films").assertExists()
    }

    @Test
    fun needsLibraryOffersStartOverSinceItIsAfterTheFirstStep() {
        show(SetupUiState.NeedsLibrary(choices = listOf(LibraryOption("films", "Family films"))))
        compose.onNodeWithText("Start over").assertExists()
    }

    @Test
    fun failedShowsItsMessageAndOffersStartOver() {
        show(SetupUiState.Failed("This device's secure storage could not be read."))
        compose.onNodeWithText("This device's secure storage could not be read.").assertExists()
        compose.onNodeWithText("Start over").assertExists()
    }

    @Test
    fun startOverOpensTheSameConfirmationTheWordingPromises() {
        show(SetupUiState.Failed("Storage could not be read."))

        compose.onNodeWithText("Start over").performSemanticsAction(SemanticsActions.OnClick)

        compose.onNodeWithText("Start over?").assertExists()
        compose.onNodeWithText("signs this device out of Telegram", substring = true).assertExists()
    }

    private fun show(state: SetupUiState) {
        compose.runOnUiThread {
            fixture = TvAppFixture(TvSetupStage.APPLICATION)
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { TvTheme { TvSetupStep(state = state, viewModel = fixture.setup) } }
        }
        compose.waitForIdle()
    }
}

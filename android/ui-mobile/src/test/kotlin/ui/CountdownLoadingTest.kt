package ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import catalog.profile.ProfileUiState
import designsystem.COUNTDOWN_LEADER_TEST_TAG
import java.io.File
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.catalog.home.LibraryLoading
import ui.player.PreparingNote
import ui.profile.PickerActions
import ui.profile.ProfilePickerScreen
import ui.setup.LibraryScreen

/** Every wait shows the web's film leader counting down (`index.html:100-116`), and nothing on screen is a spinner. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CountdownLoadingTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { MaterialTheme { content() } }
        }
        compose.waitForIdle()
    }

    private fun assertLeaderShows() = compose.onNodeWithTag(COUNTDOWN_LEADER_TEST_TAG).assertIsDisplayed()

    /** A spinner reports itself as an indeterminate progress bar; the leader never does. */
    private fun assertNothingSpins() =
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)

    @Test fun theLibraryLoadsUnderACoverShapedBlockWithTheLeaderAboveTheWebsWords() {
        show { LibraryLoading() }
        assertLeaderShows()
        compose.onNodeWithText("Loading your library…").assertIsDisplayed()
        assertNothingSpins()
    }

    @Test fun theProfileGateWaitsUnderTheSameBlock() {
        show { ProfilePickerScreen(ProfileUiState.Loading, PickerActions({}, {}, {}, {}, {}, {})) }
        assertLeaderShows()
        compose.onNodeWithText("Loading your library…").assertIsDisplayed()
        assertNothingSpins()
    }

    @Test fun choosingALibraryWaitsInTheWebsWords() {
        show { LibraryScreen(choices = null, error = null, onChoose = {}, onLookAgain = {}) }
        compose.onNodeWithText("Reading the account's channels…").assertIsDisplayed()
        assertNothingSpins()
    }

    @Test fun aTitleOpensUnderTheLeaderAloneAndTalkBackHearsLoading() {
        show { PreparingNote() }
        assertLeaderShows()
        compose.onNodeWithContentDescription("Loading").assertIsDisplayed()
        assertNothingSpins()
    }

    @Test fun noScreenDrawsASpinner() {
        val sources = File("src/main/kotlin")
        assertTrue(sources.isDirectory, "the guard must run from ui-mobile, found no ${sources.absolutePath}")
        val spinner = Regex("""^import androidx\.compose\.material3\.CircularProgressIndicator$""", RegexOption.MULTILINE)
        val offenders =
            sources.walkTopDown()
                .filter { it.isFile && it.extension == "kt" && spinner.containsMatchIn(it.readText()) }
                .map { it.path }
                .toList()
        assertTrue(offenders.isEmpty(), "loading shows the countdown leader; spinners remain in: $offenders")
    }
}

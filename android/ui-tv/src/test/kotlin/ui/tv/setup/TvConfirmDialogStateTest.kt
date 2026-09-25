package ui.tv.setup

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
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
 * What Robolectric can check about [TvConfirmDialog] without a real window
 * manager: the title and body it was handed are what's on screen. Initial
 * focus and Back are real window-manager behaviour, plus this dialog opens
 * a second `Dialog` window Robolectric does not model faithfully, so both
 * live in `ui-tv/src/androidTest/kotlin/ui/tv/setup/TvConfirmDialogTest.kt`
 * instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TvConfirmDialogStateTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    @Test
    fun theTitleAndBodyItWasHandedAreOnScreen() {
        show {
            TvConfirmDialog(
                title = "Start over?",
                body = "This forgets everything entered so far.",
                confirmLabel = "Start over",
                confirm = {},
                cancel = {},
            )
        }

        compose.onNodeWithText("Start over?").assertExists()
        compose.onNodeWithText("This forgets everything entered so far.").assertExists()
    }

    /**
     * [TvConfirmDialog.confirmLabel] names the action — "Start over",
     * "Sign out", "Delete list" on the phone — and has no default for
     * exactly that reason; this pins that it actually reaches the button
     * rather than a hardcoded "Confirm" a caller cannot override.
     * [TvConfirmDialog.cancelLabel] is checked at its own default here,
     * since the label-override test below already proves it can change.
     */
    @Test
    fun theGivenConfirmLabelReachesTheConfirmButton() {
        show {
            TvConfirmDialog(
                title = "Sign out?",
                body = "This signs this device out.",
                confirmLabel = "Sign out",
                confirm = {},
                cancel = {},
            )
        }

        compose.onNodeWithText("Sign out").assertExists()
        compose.onNodeWithText("Cancel").assertExists()
    }

    @Test
    fun anOverriddenCancelLabelReachesTheCancelButton() {
        show {
            TvConfirmDialog(
                title = "Delete list?",
                body = "This deletes the list for good.",
                confirmLabel = "Delete list",
                confirm = {},
                cancel = {},
                cancelLabel = "Keep it",
            )
        }

        compose.onNodeWithText("Delete list").assertExists()
        compose.onNodeWithText("Keep it").assertExists()
    }

    private fun show(content: @Composable () -> Unit) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { TvTheme { content() } }
        }
        compose.waitForIdle()
    }
}

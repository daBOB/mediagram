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
                confirm = {},
                cancel = {},
            )
        }

        compose.onNodeWithText("Start over?").assertExists()
        compose.onNodeWithText("This forgets everything entered so far.").assertExists()
    }

    private fun show(content: @Composable () -> Unit) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { TvTheme { content() } }
        }
        compose.waitForIdle()
    }
}

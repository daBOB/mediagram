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
 * What Robolectric can check about [TvTextQuestion] without a real window
 * manager: the prompt it was handed is the prompt on screen. Focus and the
 * keyboard's action key are real window-manager behaviour and live in
 * `ui-tv/src/androidTest/kotlin/ui/tv/setup/TvTextQuestionTest.kt` instead —
 * `assertIsDisplayed()`/`performClick()` misbehave against tv-material
 * under Robolectric, so this checks with `assertExists()` the way
 * `TvAppTest` does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TvTextQuestionStateTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    @Test
    fun thePromptItWasHandedIsThePromptOnScreen() {
        show { TvTextQuestion(prompt = "What's your api_id?", value = "", onValue = {}, onSubmit = {}) }

        compose.onNodeWithText("What's your api_id?").assertExists()
    }

    private fun show(content: @Composable () -> Unit) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { TvTheme { content() } }
        }
        compose.waitForIdle()
    }
}

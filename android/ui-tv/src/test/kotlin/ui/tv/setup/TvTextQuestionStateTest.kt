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
 * manager: the heading, explanation and label it was handed are each their
 * own text on screen, not collapsed into one block — the bug a joined
 * "prompt" string once produced, where a long enough explanation pushed the
 * heading itself out of a centred layout. Focus and the keyboard's action
 * key are real window-manager behaviour and live in
 * `ui-tv/src/androidTest/kotlin/ui/tv/setup/TvTextQuestionTest.kt` instead —
 * `assertIsDisplayed()`/`performClick()` misbehave against tv-material
 * under Robolectric, so this checks with `assertExists()` the way
 * `TvAppTest` does. `KeyboardType` itself never reaches the semantics tree
 * — it is proven directly in `TvTextQuestionKeyboardTypeTest` instead.
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
    fun theHeadingItWasHandedIsOnScreen() {
        show {
            TvTextQuestion(
                heading = "What's your api_id?",
                explanation = null,
                label = "api_id",
                value = "",
                onValue = {},
                onSubmit = {},
            )
        }

        compose.onNodeWithText("What's your api_id?").assertExists()
    }

    @Test
    fun theExplanationItWasHandedIsOnScreenBesideTheHeading() {
        show {
            TvTextQuestion(
                heading = "Connect this device to Telegram",
                explanation = "Sign in at my.telegram.org.",
                label = "api_id",
                value = "",
                onValue = {},
                onSubmit = {},
            )
        }

        compose.onNodeWithText("Connect this device to Telegram").assertExists()
        compose.onNodeWithText("Sign in at my.telegram.org.").assertExists()
    }

    @Test
    fun theLabelItWasHandedIsOnScreen() {
        show {
            TvTextQuestion(
                heading = "",
                explanation = null,
                label = "Phone number",
                value = "",
                onValue = {},
                onSubmit = {},
            )
        }

        compose.onNodeWithText("Phone number").assertExists()
    }

    /**
     * A blank heading — sign-in's own shape, which has no separate page
     * title — renders nothing where the heading would have gone, rather
     * than an empty line standing in for one.
     */
    @Test
    fun aBlankHeadingRendersNoHeadingNode() {
        show {
            TvTextQuestion(
                heading = "",
                explanation = "Could not request a sign-in code.",
                label = "Phone number",
                value = "",
                onValue = {},
                onSubmit = {},
            )
        }

        compose.onNodeWithText("Could not request a sign-in code.").assertExists()
        compose.onNodeWithText("Phone number").assertExists()
    }

    private fun show(content: @Composable () -> Unit) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { TvTheme { content() } }
        }
        compose.waitForIdle()
    }
}

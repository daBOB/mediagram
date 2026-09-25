package ui.tv.setup

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ui.tv.TvTheme

/**
 * The two behaviours [TvTextQuestion] exists for — focus lands in the
 * field without a person having to reach for it, and the on-screen
 * keyboard's own action key is the only way the question gets answered —
 * only run true on a real window manager. Robolectric's tv-material nodes
 * are known to misbehave for exactly this (`TvFocusTest`'s history), which
 * is why this set is instrumented rather than JVM-only.
 *
 * A fixture-free `ComponentActivity` hosts the composable directly: no
 * `SetupViewModel`, no Hilt, no Telegram account — [TvTextQuestion] takes
 * its value and callbacks as plain parameters, so a test double for either
 * is a local `remember` block, not a mocked dependency graph.
 */
@RunWith(AndroidJUnit4::class)
class TvTextQuestionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theFieldIsFocusedAsSoonAsTheScreenAppears() {
        compose.setContent {
            TvTheme {
                TvTextQuestion(prompt = "What's the answer?", value = "", onValue = {}, onSubmit = {})
            }
        }

        compose.onNodeWithTag(TvTextQuestionFieldTag).assertIsFocused()
    }

    @Test
    fun theKeyboardsActionKeySubmitsAFilledField() {
        var submitted = false

        compose.setContent {
            TvTheme {
                var value by remember { mutableStateOf("") }
                TvTextQuestion(
                    prompt = "What's the answer?",
                    value = value,
                    onValue = { value = it },
                    onSubmit = { submitted = true },
                )
            }
        }

        compose.onNodeWithTag(TvTextQuestionFieldTag).performTextInput("42")
        compose.onNodeWithTag(TvTextQuestionFieldTag).performImeAction()

        assertTrue(submitted)
    }
}

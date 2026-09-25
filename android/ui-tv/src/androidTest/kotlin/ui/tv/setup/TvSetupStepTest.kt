package ui.tv.setup

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.Text
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import setup.LibraryOption
import ui.tv.TvTheme

/**
 * The focus and D-pad behaviour [TvSetupStep]'s screens exist for, which
 * only run true on a real window manager — Robolectric's tv-material nodes
 * are known to misbehave for exactly this (`TvFocusTest`'s history), which
 * is why this lives in this module's `androidTest` set rather than beside
 * `TvSetupStepTest` in `test`. State and appearance are proven there
 * instead.
 *
 * `TvApplicationScreen` and `TvLibraryChoiceScreen` take plain parameters,
 * so a fixture-free `ComponentActivity` hosts them directly — no
 * `SetupViewModel`, no Hilt, no Telegram account. `TvSignInScreen` resolves
 * its own `LoginViewModel` through `hiltViewModel()` and is not covered
 * here for that reason; the field-focused-on-arrival behaviour it renders
 * through is the same [TvTextQuestion] `TvTextQuestionTest` already proves
 * this way, so retesting it through sign-in would only prove the same
 * mechanism twice.
 */
@RunWith(AndroidJUnit4::class)
class TvSetupStepTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    /**
     * A freshly launched test `Activity` starts in Android's touch mode,
     * where `requestFocus()` is quietly ignored by anything that is not
     * itself focusable-in-touch-mode — a text field is, by Android
     * convention, which is why `TvTextQuestionTest` and the application
     * step below need none of this. A real remote's first press leaves
     * touch mode for the rest of the session the same way; the TV
     * screenshot check separately confirms the real app's own
     * `LEANBACK_LAUNCHER` activity never starts in it to begin with. One
     * harmless key press injected at the OS level, before any content is
     * set, is the standard fix for this instrumented-test-only gap — not a
     * workaround for a defect in the screens themselves.
     */
    @Before
    fun leaveTouchMode() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent KEYCODE_DPAD_CENTER").close()
    }

    @Test
    fun theApiIdFieldIsFocusedAsSoonAsApplicationStepAppears() {
        compose.setContent {
            TvTheme { TvApplicationScreen(error = null, onSubmit = { _, _ -> }) }
        }

        waitUntilFocused(TvTextQuestionFieldTag)
    }

    @Test
    fun theFirstLibraryRowIsFocusedAsSoonAsTheChoiceScreenAppears() {
        showLibraryChoices()

        waitUntilFocused(TvLibraryFirstRowTag)
    }

    @Test
    fun dPadDownMovesFocusFromTheFirstLibraryRowToTheNext() {
        showLibraryChoices()
        waitUntilFocused(TvLibraryFirstRowTag)

        compose.onNodeWithTag(TvLibraryFirstRowTag).performKeyInput { pressKey(Key.DirectionDown) }

        waitUntilFocused("tv-library-row-shows")
    }

    @Test
    fun theStartOverRowOpensTheConfirmationWithCancelFocused() {
        compose.setContent {
            TvTheme {
                TvWithStartOver(onStartOver = {}, focusStartOver = false) {
                    Text("content")
                }
            }
        }

        compose.onNodeWithTag(TvStartOverRowTag).performClick()

        waitUntilFocused(TvConfirmDialogCancelTag)
    }

    private fun showLibraryChoices() {
        compose.setContent {
            TvTheme {
                TvLibraryChoiceScreen(
                    choices = listOf(LibraryOption("films", "Family films"), LibraryOption("shows", "Shows")),
                    error = null,
                    onChoose = {},
                    onLookAgain = {},
                )
            }
        }
    }

    /**
     * A node's own `LaunchedEffect(Unit) { requester.requestFocus() }` runs
     * on the composition's first pass, but the Android window that actually
     * grants it real focus — the host Activity settling in, or a freshly
     * opened `Dialog`'s own window — does so on its own asynchronous
     * schedule that Compose's idle tracking does not wait on. Polling with
     * `ComposeTestRule.waitUntil` is what every focus assertion in this
     * class needs for that reason, not a fixed sleep.
     */
    private fun waitUntilFocused(tag: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
    }
}

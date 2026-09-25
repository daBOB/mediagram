package ui.tv.setup

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ui.tv.LeavesTouchModeRule
import ui.tv.TvTheme

/**
 * Cancel taking initial focus and Back resolving to Cancel only run true on
 * a real window manager: [TvConfirmDialog] hosts its content in its own
 * `Dialog` window, and Robolectric's tv-material nodes are known to
 * misbehave for focus assertions (`TvFocusTest`'s history) even before a
 * second window enters the picture.
 */
@RunWith(AndroidJUnit4::class)
class TvConfirmDialogTest {
    @get:Rule val touchMode = LeavesTouchModeRule()

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun cancelIsFocusedAsSoonAsTheDialogAppears() {
        show()

        compose.onNodeWithTag(TvConfirmDialogCancelTag).assertIsFocused()
    }

    @Test
    fun backResolvesToCancelNotConfirm() {
        var confirmed = false
        var cancelled = false
        show(onConfirm = { confirmed = true }, onCancel = { cancelled = true })

        Espresso.pressBack()

        assertTrue(cancelled)
        assertFalse(confirmed)
    }

    @Test
    fun dPadRightThenEnterConfirms() {
        var confirmed = false
        show(onConfirm = { confirmed = true })

        compose.onNodeWithTag(TvConfirmDialogCancelTag).performKeyInput {
            pressKey(Key.DirectionRight)
        }
        compose.onNodeWithTag(TvConfirmDialogConfirmTag).assertIsFocused()
        compose.onNodeWithTag(TvConfirmDialogConfirmTag).performKeyInput {
            pressKey(Key.Enter)
        }

        assertTrue(confirmed)
    }

    private fun show(
        onConfirm: () -> Unit = {},
        onCancel: () -> Unit = {},
    ) {
        compose.setContent {
            TvTheme {
                TvConfirmDialog(
                    title = "Start over?",
                    body = "This forgets everything entered so far.",
                    confirmLabel = "Start over",
                    confirm = onConfirm,
                    cancel = onCancel,
                )
            }
        }
    }
}

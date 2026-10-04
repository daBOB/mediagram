package ui.profile

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import catalog.profile.PinPrompt
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PinDialogTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(
        prompt: PinPrompt,
        onPin: (String) -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { MaterialTheme { PinDialog(prompt, onPin, onDismiss) } }
        }
        compose.waitForIdle()
    }

    @Test fun fourDigitsAreHandedOverAndTheFieldStartsAgain() {
        val entered = mutableListOf<String>()
        show(PinPrompt("andre’s PIN", newPin = false), onPin = { entered += it })
        compose.onNodeWithText("andre’s PIN").assertExists()
        compose.onNodeWithTag(PinFieldTag).performTextInput("12a3")
        assertEquals(emptyList(), entered)
        compose.onNodeWithTag(PinFieldTag).performTextInput("4")
        assertEquals(listOf("1234"), entered)
        compose.onNodeWithTag(PinFieldTag).performTextInput("5678")
        assertEquals(listOf("1234", "5678"), entered)
    }

    /** A new PIN is typed twice, and a mistyped one is often typed again: the same four digits go over each time. */
    @Test fun theSameFourDigitsAgainAreHandedOverAgain() {
        val entered = mutableListOf<String>()
        show(PinPrompt("A PIN for Ann", newPin = true), onPin = { entered += it })
        compose.onNodeWithTag(PinFieldTag).performTextInput("1234")
        compose.onNodeWithTag(PinFieldTag).performTextInput("1234")
        assertEquals(listOf("1234", "1234"), entered)
    }

    @Test fun whatIsTypedIsNeverShown() {
        show(PinPrompt("andre’s PIN", newPin = false))
        compose.onNodeWithTag(PinFieldTag).performTextInput("12")
        compose.onNodeWithTag(PinFieldTag).assertTextEquals("PIN", "••")
    }

    @Test fun aNewPinsSecondEntryAndAWaitAreSaid() {
        show(PinPrompt("Choose a PIN for test", newPin = true, confirming = true, error = "Too many wrong PINs. Try again in 42 s."))
        compose.onNodeWithText("The new PIN again").assertExists()
        compose.onNodeWithText("Too many wrong PINs. Try again in 42 s.").assertExists()
    }

    @Test fun cancelLeavesWithoutAPin() {
        var dismissed = false
        show(PinPrompt("andre’s PIN", newPin = false), onDismiss = { dismissed = true })
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(dismissed)
    }
}

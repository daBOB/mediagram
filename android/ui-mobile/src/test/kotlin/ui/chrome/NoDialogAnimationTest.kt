package ui.chrome

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.window.Dialog
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import ui.catalog.FetchResultDialog

/**
 * A dialog window's open and close animations come from its attributes, which
 * Robolectric's framework keeps as the device would. Whether the open is
 * actually still is the device walk's to see.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NoDialogAnimationTest {
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

    private fun latestWindowAnimations() = ShadowDialog.getLatestDialog().window!!.attributes.windowAnimations

    @Test fun anAlertDialogsWindowHasNoAnimation() {
        show { FetchResultDialog(message = "Nothing new.", onDismiss = {}) }
        compose.onNodeWithText("Nothing new.").assertExists()
        assertEquals(0, latestWindowAnimations())
    }

    @Test fun aFullScreenDialogsWindowHasNone() {
        show {
            Dialog(onDismissRequest = {}) {
                NoDialogAnimation()
                Text("reel")
            }
        }
        compose.onNodeWithText("reel").assertExists()
        assertEquals(0, latestWindowAnimations())
    }

    /** Each `AlertDialog(` or `Dialog(` call is matched by a `NoDialogAnimation()` in the same file. */
    @Test fun everyDialogCallsIt() {
        val dialogs = Regex("""(?<![A-Za-z.])(AlertDialog|Dialog)\(""")
        val calls = Regex("""NoDialogAnimation\(\)""")
        val offenders =
            File("src/main/kotlin").walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { file -> file.readText().let { dialogs.findAll(it).count() > calls.findAll(it).count() } }
                .map { it.path }
                .toList()
        assertTrue(offenders.isEmpty(), "call NoDialogAnimation() first inside each dialog in: $offenders")
    }
}

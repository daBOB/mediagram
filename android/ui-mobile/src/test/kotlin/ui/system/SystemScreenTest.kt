package ui.system

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import designsystem.MediagramTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import system.SystemUiState
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SystemScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var controller: ActivityController<SystemTestActivity>? = null

    @After fun close() {
        compose.runOnUiThread { controller?.close() }
        SystemTestActivity.content = {}
    }

    @Test fun aFirstReadFailureOffersRetryAndTheRecoveredFactsAppear() {
        val snapshot = mutableStateOf<SystemUiState?>(null)
        val failure = mutableStateOf<String?>("System information could not be read. Try again.")
        var retries = 0
        show {
            SystemContent(snapshot.value, failure.value) {
                retries++
                snapshot.value = facts()
                failure.value = null
            }
        }

        compose.onNodeWithText(failure.value!!).assertIsDisplayed()
        compose.onNodeWithText("Catalogue").assertDoesNotExist()
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Catalogue").assertIsDisplayed()
        compose.onNodeWithText("4 playable sets, 2 posters").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertDoesNotExist()
        assertEquals(1, retries)
    }

    @Test fun aFailedRefreshKeepsTheSnapshotVisibleAlongsideRetry() {
        var retries = 0
        show { SystemContent(facts(), "System information could not be read. Try again.") { retries++ } }

        compose.onNodeWithText("Catalogue").assertIsDisplayed()
        compose.onNodeWithText("4 playable sets, 2 posters").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
    }

    private fun show(content: @Composable () -> Unit) {
        SystemTestActivity.content = content
        compose.runOnUiThread { controller = Robolectric.buildActivity(SystemTestActivity::class.java).setup().visible() }
        compose.waitForIdle()
    }

    private fun facts() = SystemUiState("channel", 4, 2, 3, null, null, 12, 100, 0, 0, 0, 0, true, "test", 0)
}

class SystemTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MediagramTheme { content() } }
    }

    companion object {
        var content: @Composable () -> Unit = {}
    }
}

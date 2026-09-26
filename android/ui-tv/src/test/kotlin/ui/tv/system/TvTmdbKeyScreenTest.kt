package ui.tv.system

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvTmdbKeyScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    private val saved = mutableListOf<String>()

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(hasKey: Boolean) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { TvTmdbKeyScreen(hasKey = hasKey, onSave = { saved += it }) }
        }
        compose.waitForIdle()
    }

    private fun press(node: SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    @Test
    fun theKeyboardsActionKeyOnAnEmptyFieldKeepsTheStoredKey() {
        show(hasKey = true)
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitForIdle()
        assertEquals(emptyList(), saved)
    }

    @Test
    fun clearingTheKeyAsksFirstAndCancelKeepsIt() {
        show(hasKey = true)
        press(compose.onNodeWithText("Clear stored key"))
        compose.onNodeWithText("Clear the TMDB key?").assertExists()
        press(compose.onNodeWithText("Cancel"))
        assertEquals(emptyList(), saved)
    }

    @Test
    fun confirmingTheClearSavesABlank() {
        show(hasKey = true)
        press(compose.onNodeWithText("Clear stored key"))
        press(compose.onNodeWithText("Clear"))
        assertEquals(listOf(""), saved)
    }

    @Test
    fun withNoKeyStoredThereIsNothingToClear() {
        show(hasKey = false)
        compose.onNodeWithText("Clear stored key").assertDoesNotExist()
    }
}

package ui.catalog.title

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.catalog.TitleTabs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `.tab-panel` rises 8px into place each time its tab is shown
 * (`title-page.css:133-134`, `tabs.js:85-97`): on arrival, on every switch,
 * and on a page come back to; never on a redraw. The layer's translation
 * shows in the body's bounds; its fade is the device walk's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TabPanelRiseTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { MaterialTheme { content() } }
        }
        frames(2)
    }

    private fun frames(count: Int) = repeat(count) { compose.mainClock.advanceTimeByFrame() }

    private fun top(text: String) = compose.onNodeWithText(text).getUnclippedBoundsInRoot().top.value

    @Test fun eachTabsContentRisesInWhenShownAndARedrawLeavesItAtRest() {
        var unrelated by mutableStateOf(0)
        show {
            Column {
                Text("unrelated: $unrelated")
                TitleTabs(listOf("Overview", "Similar")) { tab -> Text("body: $tab") }
            }
        }
        val arriving = top("body: Overview")
        compose.mainClock.advanceTimeBy(600)
        val rest = top("body: Overview")
        assertTrue(arriving > rest + 5f, "the first panel rises in with the page: $arriving against $rest")

        compose.onNodeWithText("Similar").performClick()
        frames(2)
        assertTrue(top("body: Similar") > rest + 5f, "a newly chosen tab's content rises in")
        compose.mainClock.advanceTimeBy(600)
        assertEquals(rest, top("body: Similar"), 0.5f)

        compose.onNodeWithText("Overview").performClick()
        frames(2)
        assertTrue(top("body: Overview") > rest + 5f, "and again on going back to a tab already seen")
        compose.mainClock.advanceTimeBy(600)

        compose.runOnUiThread { unrelated = 1 }
        frames(2)
        assertEquals(rest, top("body: Overview"), 0.5f, "a redraw never replays it")
    }

    /** The frame's saved slot keeps the chosen tab across Back; the rise must play again regardless. */
    @Test fun aPageComeBackToWithBackRisesItAgain() {
        var open by mutableStateOf(true)
        show {
            val held = rememberSaveableStateHolder()
            if (open) held.SaveableStateProvider("title") { TitleTabs(listOf("Overview", "Similar")) { tab -> Text("body: $tab") } }
        }
        top("body: Overview")
        compose.mainClock.advanceTimeBy(600)
        val rest = top("body: Overview")
        compose.runOnUiThread { open = false }
        compose.waitForIdle()
        frames(2)
        compose.runOnUiThread { open = true }
        frames(1)
        compose.onAllNodesWithText("body: Overview").fetchSemanticsNodes()
        frames(1)
        assertTrue(top("body: Overview") > rest + 5f, "Back is a new visit, and the panel rises, as on the web")
    }
}

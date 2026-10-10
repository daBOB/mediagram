package ui.catalog.title

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.io.File
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.catalog.TitleTabs

/**
 * `tabs.js`'s own finding, ported: a tab chosen must survive both a page
 * rebuilt by an unrelated state change and a new tab inserted ahead of it
 * (Cast, arriving once credits load) — neither should throw the viewer
 * back to the first tab. [TitleTabs] keeps its choice by label rather than
 * position for exactly this reason.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TitleTabsTest {
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

    @Test fun selectionSurvivesAnUnrelatedRecomposition() {
        var unrelated by mutableStateOf(0)
        show {
            Text("unrelated: $unrelated")
            TitleTabs(listOf("Overview", "Similar", "Details")) { tab -> Text("body: $tab") }
        }
        compose.onNodeWithText("Similar").performClick()
        compose.onNodeWithText("body: Similar").assertIsDisplayed()

        compose.runOnUiThread { unrelated = 1 }
        compose.waitForIdle()
        compose.onNodeWithText("body: Similar").assertIsDisplayed()
    }

    @Test fun selectionFollowsItsLabelWhenATabIsInsertedAheadOfIt() {
        var labels by mutableStateOf(listOf("Overview", "Similar", "Details"))
        show { TitleTabs(labels) { tab -> Text("body: $tab") } }
        compose.onNodeWithText("Similar").performClick()
        compose.onNodeWithText("body: Similar").assertIsDisplayed()

        compose.runOnUiThread { labels = listOf("Overview", "Cast", "Similar", "Details") }
        compose.waitForIdle()
        compose.onNodeWithText("body: Similar").assertIsDisplayed()
    }

    @Test fun aTabThatDisappearsFallsBackToTheFirst() {
        var labels by mutableStateOf(listOf("Overview", "Cast", "Similar"))
        show { TitleTabs(labels) { tab -> Text("body: $tab") } }
        compose.onNodeWithText("Cast").performClick()
        compose.onNodeWithText("body: Cast").assertIsDisplayed()

        compose.runOnUiThread { labels = listOf("Overview", "Similar") }
        compose.waitForIdle()
        compose.onNodeWithText("body: Overview").assertIsDisplayed()
    }

    /**
     * Material's tab rows slide an indicator from tab to tab unless handed
     * `indicator = {}`; the web's underline fades in place under each tab
     * (`title-page.css:121-132`). A tab row added without it would slide again.
     */
    @Test fun noTabRowSlidesAnIndicator() {
        val rows = Regex("""(?<![A-Za-z.])(Primary|Secondary)?(Scrollable)?TabRow\(""")
        val still = Regex("""indicator = \{\}""")
        val offenders =
            File("src/main/kotlin").walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { file -> file.readText().let { rows.findAll(it).count() > still.findAll(it).count() } }
                .map { it.path }
                .toList()
        assertTrue(offenders.isEmpty(), "pass indicator = {} and draw each tab's own underline in: $offenders")
    }
}

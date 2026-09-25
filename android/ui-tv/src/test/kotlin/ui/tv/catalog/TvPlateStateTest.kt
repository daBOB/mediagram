package ui.tv.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.tv.TvTheme
import kotlin.test.assertEquals

/**
 * What Robolectric can check about [TvPlate] without a real window manager:
 * which mark a given [watchedFraction][TvPlate] draws, the initials
 * fallback, and that its `onClick` reaches [TvPlate.onOpen]. Initial focus
 * and the centre key are real window-manager behaviour and live in
 * `ui-tv/src/androidTest/kotlin/ui/tv/catalog/TvPlateTest.kt` instead, the
 * same split every other TV building block in this module follows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TvPlateStateTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    @Test
    fun theTitleIsOnScreen() {
        show(title = "A Quiet Film")

        compose.onNodeWithText("A Quiet Film").assertExists()
    }

    @Test
    fun withNoPosterTheInitialsStandInForTheArt() {
        show(title = "Bright Star", posterPath = null)

        compose.onNodeWithText("BS").assertExists()
    }

    // TvPlate merges its own semantics into one node (a screen reader meets
    // one plate, not an image, a mark and its name separately), which also
    // folds the progress rule's and the tick's own tags into that merged
    // node — useUnmergedTree is what still lets a test tell the two marks
    // apart from each other underneath it.
    @Test
    fun withNoFractionNeitherMarkIsDrawn() {
        show(watchedFraction = null)

        compose.onNodeWithTag(TvPlateProgressTag, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag(TvPlateWatchedTickTag, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aFractionShortOfAWholeDrawsTheProgressRuleNotTheTick() {
        show(watchedFraction = 0.4f)

        compose.onNodeWithTag(TvPlateProgressTag, useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(TvPlateWatchedTickTag, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aWholeFractionDrawsTheTickNotTheProgressRule() {
        show(watchedFraction = 1f)

        compose.onNodeWithTag(TvPlateWatchedTickTag, useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(TvPlateProgressTag, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun clickingThePlateCallsOnOpen() {
        var opened = false
        show(onOpen = { opened = true })

        compose.onNodeWithText("A Quiet Film").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(true, opened)
    }

    private fun show(
        title: String = "A Quiet Film",
        posterPath: java.io.File? = null,
        watchedFraction: Float? = null,
        onOpen: () -> Unit = {},
    ) {
        showContent {
            TvPlate(title = title, posterPath = posterPath, watchedFraction = watchedFraction, onOpen = onOpen)
        }
    }

    private fun showContent(content: @Composable () -> Unit) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { TvTheme { content() } }
        }
        compose.waitForIdle()
    }
}

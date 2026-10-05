package ui.player

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
import player.EpisodeList
import player.EpisodeRow
import player.EpisodeSection
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The episode sidebar over an [EpisodeList]: it opens on the open title's
 * season, steps through the others, says which rows are watched, partly
 * watched and playing, and hands a picked row back.
 */
abstract class EpisodeSidebarCases {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    protected val picked = mutableListOf<String>()
    protected var closed = 0

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    protected fun show(list: EpisodeList) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MaterialTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        EpisodeSidebar(
                            list = list,
                            onPick = { picked += it },
                            onClose = { closed++ },
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun row(
        id: String,
        number: String,
        title: String,
        watched: Boolean = false,
        progress: Float? = null,
        current: Boolean = false,
    ) = EpisodeRow(id, number, title, runtimeSecs = 1_380, watched = watched, progress = progress, current = current)

    protected val twoSeasons =
        EpisodeList(
            sections =
                listOf(
                    EpisodeSection("Season 1", listOf(row("a1", "S1E1", "Pilot", watched = true), row("a2", "S1E2", "Second", progress = 0.5f))),
                    EpisodeSection("Season 2", listOf(row("b1", "S2E1", "Return", current = true), row("b2", "S2E2", "Finale"))),
                ),
            currentSection = 1,
        )

    protected fun sidebarWidth(): Float = compose.onNodeWithTag(EpisodeSidebarTag).getBoundsInRoot().let { (it.right - it.left).value }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp")
class EpisodeSidebarTest : EpisodeSidebarCases() {
    @Test
    fun itOpensOnTheSeasonOfTheOpenTitle() {
        show(twoSeasons)

        compose.onNodeWithText("Season 2").assertExists()
        compose.onNodeWithText("Return").assertExists()
        compose.onNodeWithText("Pilot", substring = true).assertDoesNotExist()
    }

    @Test
    fun theArrowsStepThroughTheSeasonsAndStopAtTheEnds() {
        show(twoSeasons)
        compose.onNodeWithContentDescription("Next season").assertIsNotEnabled()

        compose.onNodeWithContentDescription("Previous season").performClick()

        compose.onNodeWithText("Season 1").assertExists()
        compose.onNodeWithContentDescription("Previous season").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Next season").assertIsEnabled()
    }

    @Test
    fun oneSeasonIsATitleWithoutArrows() {
        show(EpisodeList(listOf(twoSeasons.sections[1]), currentSection = 0))

        compose.onNodeWithText("Season 2").assertExists()
        compose.onNodeWithContentDescription("Previous season").assertDoesNotExist()
        compose.onNodeWithContentDescription("Next season").assertDoesNotExist()
    }

    @Test
    fun theOpenTitleReadsNowPlayingAndIsNotActionable() {
        show(twoSeasons)

        compose.onNodeWithText(NOW_PLAYING).assertExists()
        compose.onNode(hasText("Return") and hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun aRowSaysItsRuntime() {
        show(twoSeasons)

        compose.onNode(hasText("Finale") and hasText("23m")).assertExists()
    }

    @Test
    fun aWatchedRowIsTickedAndStillPlayable() {
        show(twoSeasons)
        compose.onNodeWithContentDescription("Previous season").performClick()

        compose.onNode(hasText("✓ Pilot") and hasClickAction()).performClick()

        assertEquals(listOf("a1"), picked)
    }

    @Test
    fun aPartWatchedRowCarriesAProgressLine() {
        show(twoSeasons)
        compose.onNodeWithContentDescription("Previous season").performClick()

        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.5f, 0f..1f)), useUnmergedTree = true).assertExists()
    }

    @Test
    fun closeAsksToClose() {
        show(twoSeasons)

        compose.onNodeWithContentDescription("Close episodes").performClick()

        assertEquals(1, closed)
    }

    @Test
    fun besideThePictureItIs320dpWide() {
        show(twoSeasons)

        assertTrue(sidebarWidth() in 319.5f..320.5f, "sidebar is ${sidebarWidth()}dp")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class EpisodeSidebarNarrowTest : EpisodeSidebarCases() {
    @Test
    fun underSixHundredDpItTakesTheWholeWidth() {
        show(twoSeasons)

        assertTrue(sidebarWidth() in 359.5f..360.5f, "sidebar is ${sidebarWidth()}dp")
    }
}

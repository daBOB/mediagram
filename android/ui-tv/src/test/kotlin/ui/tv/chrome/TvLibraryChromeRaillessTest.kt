package ui.tv.chrome

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import catalog.ChromeCounts
import designsystem.TvStyle
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.tv.catalog.TvScreenStateTest
import ui.tv.catalog.toTopWhenLeftForTheBar
import ui.tv.profile.TvChosenProfile

/**
 * Apple TV and Google TV styles draw no rail: the page starts at the
 * screen's own edge, Left at its start goes nowhere, Back and Up from the
 * page reach the selected pill — or, on a kept wall with none selected, the
 * avatar whose menu holds the rail's rows — and Back from the bar leaves the
 * app, the way Back at the top of any TV app does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvLibraryChromeRaillessTest : TvScreenStateTest() {
    @Test
    fun thePageStartsAtTheScreensEdgeWithNoRail() {
        for (style in listOf(TvStyle.APPLE, TvStyle.GOOGLE)) {
            show(style) { chrome() }
            assertEquals(0.dp, compose.onNodeWithTag(CONTENT).getUnclippedBoundsInRoot().left, "$style")
            close()
        }
        show(TvStyle.MEDIAGRAM) { chrome() }
        // Whole pixels: the rail's 60.75dp edge lands on the 61st.
        assertEquals(TvContentStart.value, compose.onNodeWithTag(CONTENT).getUnclippedBoundsInRoot().left.value, 0.5f)
    }

    @Test
    fun leftAtThePagesStartStaysOnThePage() {
        show(TvStyle.APPLE) { chrome() }
        val content = compose.onNodeWithTag(CONTENT)
        content.performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        content.performKeyInput { pressKey(Key.DirectionLeft) }
        content.assertIsFocused()
    }

    @Test
    fun leftFromAPlateBelowTheBarDoesNotJumpToTheBarsControls() {
        // Search sits above and to the left of the plate: a geometric Left would take it.
        show(TvStyle.APPLE) { chrome(plateStart = 400.dp) }
        val content = compose.onNodeWithTag(CONTENT)
        content.performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        content.performKeyInput { pressKey(Key.DirectionLeft) }
        compose.waitForIdle()
        content.assertIsFocused()
    }

    @Test
    fun backFromThePageReachesTheSelectedPillThenLeavesTheApp() {
        show(TvStyle.GOOGLE) { chrome() }
        compose.onNodeWithTag(CONTENT).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        back()
        compose.onNodeWithText("Home").assertIsFocused()
        assertFalse(pageTakesBack(), "the bar has nothing behind it: Back closes the app")
    }

    @Test
    fun upFromThePageReachesTheSelectedPill() {
        show(TvStyle.APPLE) { chrome(selectedPill = 1) }
        val content = compose.onNodeWithTag(CONTENT)
        content.performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        content.performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Movies").assertIsFocused()
    }

    /**
     * A row scrolled up under the floating bar puts its card's top above the
     * pills' own, and 2-D search only takes a stop whose top is higher still:
     * on the box it found none, Up did nothing, and only Back got out.
     */
    @Test
    fun upFromAStopUnderTheBarStillReachesTheSelectedPill() {
        var toTop = false
        show(TvStyle.APPLE) {
            chrome(selectedPill = 1) {
                LazyColumn(Modifier.toTopWhenLeftForTheBar { toTop = true }) {
                    item { Box(Modifier.size(200.dp).testTag(CONTENT).focusable()) }
                }
            }
        }
        val content = compose.onNodeWithTag(CONTENT)
        val pill = compose.onNodeWithText("Movies")
        val (stop, pills) = content.getUnclippedBoundsInRoot().top to pill.getUnclippedBoundsInRoot().top
        assertTrue(stop < pills, "the stop starts above the pills: $stop against $pills")
        content.performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()

        content.performKeyInput { pressKey(Key.DirectionUp) }
        compose.waitForIdle()

        pill.assertIsFocused()
        assertTrue(toTop, "leaving for the bar still takes the page back to its top")
    }

    @Test
    fun upWithAStopAboveItInThePageStaysInThePage() {
        show(TvStyle.GOOGLE) {
            chrome {
                LazyColumn {
                    item { Box(Modifier.size(200.dp).testTag(ABOVE).focusable()) }
                    item { Box(Modifier.size(200.dp).testTag(CONTENT).focusable()) }
                }
            }
        }
        val content = compose.onNodeWithTag(CONTENT)
        content.performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()

        content.performKeyInput { pressKey(Key.DirectionUp) }
        compose.waitForIdle()

        compose.onNodeWithTag(ABOVE).assertIsFocused()
    }

    @Test
    fun backFromAKeptWallReachesTheAvatar() {
        show(TvStyle.APPLE) { chrome(selectedPill = -1) }
        compose.onNodeWithTag(CONTENT).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        back()
        compose.onNodeWithContentDescription(AVATAR).assertIsFocused()
    }

    @Composable
    private fun chrome(
        selectedPill: Int = 0,
        plateStart: Dp = 0.dp,
        page: @Composable () -> Unit = {
            // A scrolling page below the bar, as a real one is: a lone focusable never reports Up leaving it.
            LazyColumn {
                item { Spacer(Modifier.height(120.dp)) }
                item { Box(Modifier.padding(start = plateStart).size(200.dp).testTag(CONTENT).focusable()) }
            }
        },
    ) =
        TvLibraryChrome(
            pills = DEPARTMENTS,
            selectedPill = selectedPill,
            onSelectPill = {},
            railActive = null,
            counts = ChromeCounts(myList = 0, continueWatching = 0, perShelf = emptyMap(), collections = 0),
            tally = emptyList(),
            onRailSelect = {},
            onSearch = {},
            profile = TvChosenProfile(name = "Ada", onChoose = {}),
            onMenu = {},
            focus = rememberTvChromeFocus(),
            content = page,
        )

    private companion object {
        val DEPARTMENTS = listOf("Home", "Movies", "Series", "Anime", "Documentaries", "Tutorials", "Collections")
        const val AVATAR = "Menu, watching as Ada"
        const val CONTENT = "content"
        const val ABOVE = "above"
    }
}

package ui.tv.chrome

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.unit.dp
import catalog.ChromeCounts
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ui.tv.catalog.TvScreenStateTest
import ui.tv.profile.TvChosenProfile
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvLibraryChromeUpTest : TvScreenStateTest() {
    /**
     * Up past a page's top row goes to the selected pill, not to whatever
     * bar control happens to sit above the focused card: on the box a
     * Series card under the Movies pill reached Movies, and one far to the
     * right reached the search button.
     */
    /**
     * Left from Search reaches the last pill even when the row has scrolled
     * it out of view: on the box it skipped Tutorials and Collections for
     * the nearest pill still in sight. The pill taking the remote scrolls
     * the row back to it.
     */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun leftFromSearchReachesTheLastPillScrolledOutOfView() {
        val pills =
            listOf("Home" to null, "Movies" to 1243, "Series" to 318, "Anime" to 52, "Documentaries" to 67, "Tutorials" to 12, "Collections" to 109)
                .map { (title, count) -> TvDepartmentPill(title, count) }
        show { chrome(pills, selectedPill = 0) { Spacer(Modifier.height(200.dp)) } }
        val search = compose.onNodeWithContentDescription("Search")
        search.performSemanticsAction(SemanticsActions.RequestFocus)
        val collections = compose.onNodeWithText("Collections")
        assertTrue(
            collections.getUnclippedBoundsInRoot().left >= search.getUnclippedBoundsInRoot().left,
            "the last pill starts out of view: ${collections.getUnclippedBoundsInRoot()} against ${search.getUnclippedBoundsInRoot()}",
        )

        search.performKeyInput { pressKey(Key.DirectionLeft) }

        collections.assertIsFocused()
        assertTrue(collections.getUnclippedBoundsInRoot().right <= search.getUnclippedBoundsInRoot().left, "the row scrolls the last pill into view")
    }

    /**
     * A focused pill grows by [ui.tv.TvFocus.Scale] about its centre, past its
     * own bounds, and the row clips at its edges: on the box the Collections
     * ring was cut where the row meets Search. The pill the row scrolls to
     * keeps room for that growth on either side; the last one is the case seen.
     */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun theLastPillKeepsRoomForItsRingBeforeSearch() {
        val pills =
            listOf("Home" to null, "Movies" to 1243, "Series" to 318, "Anime" to 52, "Documentaries" to 67, "Tutorials" to 12, "Collections" to 109)
                .map { (title, count) -> TvDepartmentPill(title, count) }
        show { chrome(pills, selectedPill = 0) { Spacer(Modifier.height(200.dp)) } }
        val search = compose.onNodeWithContentDescription("Search")
        search.performSemanticsAction(SemanticsActions.RequestFocus)
        search.performKeyInput { pressKey(Key.DirectionLeft) }
        compose.waitForIdle()

        val collections = compose.onNodeWithText("Collections").getUnclippedBoundsInRoot()
        val growth = (collections.right - collections.left) * (ui.tv.TvFocus.Scale - 1f) / 2f
        val rowEnd = search.getUnclippedBoundsInRoot().left
        assertTrue(collections.right + growth <= rowEnd, "the last pill's ring clears the row's end: $collections, growth $growth, row ends at $rowEnd")
    }

    @Test
    fun upPastThePagesTopRowReachesTheSelectedPill() {
        val last = FocusRequester()
        show {
            chrome(listOf(TvDepartmentPill("Home", null), TvDepartmentPill("Movies", 3), TvDepartmentPill("Series", 2)), selectedPill = 2) {
                LazyColumn {
                    // A hero with nothing to focus, as every department page opens with.
                    item { Spacer(Modifier.height(200.dp)) }
                    item {
                        LazyRow {
                            items(6) { index ->
                                val tag = "card$index"
                                Box(Modifier.size(120.dp).testTag(tag).let { if (index == 5) it.focusRequester(last) else it }.focusable())
                            }
                        }
                    }
                }
                LaunchedEffect(Unit) { last.requestFocus() }
            }
        }
        compose.onNodeWithTag("card5").assertIsFocused()

        compose.onNodeWithTag("card5").performKeyInput { pressKey(Key.DirectionUp) }

        compose.onNodeWithText("Series").assertIsFocused()
    }

    @Composable
    private fun chrome(
        pills: List<TvDepartmentPill>,
        selectedPill: Int,
        content: @Composable () -> Unit,
    ) = TvLibraryChrome(
        pills = pills,
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
        content = content,
    )
}

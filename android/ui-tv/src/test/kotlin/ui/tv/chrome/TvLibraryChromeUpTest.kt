package ui.tv.chrome

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import catalog.ChromeCounts
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.tv.catalog.TvScreenStateTest
import ui.tv.profile.TvChosenProfile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvLibraryChromeUpTest : TvScreenStateTest() {
    /**
     * Up past a page's top row goes to the selected pill, not to whatever
     * bar control happens to sit above the focused card: on the box a
     * Series card under the Movies pill reached Movies, and one far to the
     * right reached the search button.
     */
    @Test
    fun upPastThePagesTopRowReachesTheSelectedPill() {
        val last = FocusRequester()
        show {
            TvLibraryChrome(
                pills = listOf(TvDepartmentPill("Home", null), TvDepartmentPill("Movies", 3), TvDepartmentPill("Series", 2)),
                selectedPill = 2,
                onSelectPill = {},
                railActive = null,
                counts = ChromeCounts(myList = 0, continueWatching = 0, perShelf = emptyMap(), collections = 0),
                tally = emptyList(),
                onRailSelect = {},
                onSearch = {},
                profile = TvChosenProfile(name = "Ada", onChoose = {}),
                onMenu = {},
                focus = rememberTvChromeFocus(),
            ) {
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

    /**
     * A focus clear with no key event behind it — the shape the generic
     * recovery rule exists for, whichever of Compose's own three mechanisms
     * produces it on the box — forced directly here rather than waited for,
     * since Robolectric cannot run the lazy-layout deactivation one of the
     * three depends on. Content still finds its way back to its own stop
     * rather than resting on the bar's first pill.
     */
    @Test
    fun aFocusClearWithNoKeyEventBehindItReturnsToContentNotTheBar() {
        val last = FocusRequester()
        lateinit var focusManager: FocusManager
        show {
            focusManager = LocalFocusManager.current
            TvLibraryChrome(
                pills = listOf(TvDepartmentPill("Home", null), TvDepartmentPill("Movies", 3), TvDepartmentPill("Series", 2)),
                selectedPill = 2,
                onSelectPill = {},
                railActive = null,
                counts = ChromeCounts(myList = 0, continueWatching = 0, perShelf = emptyMap(), collections = 0),
                tally = emptyList(),
                onRailSelect = {},
                onSearch = {},
                profile = TvChosenProfile(name = "Ada", onChoose = {}),
                onMenu = {},
                focus = rememberTvChromeFocus(),
            ) {
                Box(Modifier.size(120.dp).testTag("card").focusRequester(last).focusable())
                LaunchedEffect(Unit) { last.requestFocus() }
            }
        }
        compose.onNodeWithTag("card").assertIsFocused()

        // No key dispatched here at all — this is Compose's own fallback
        // landing on the bar, not a remote's Up, being reproduced directly.
        compose.runOnUiThread { focusManager.clearFocus(force = true) }
        compose.waitForIdle()

        compose.onNodeWithTag("card").assertIsFocused()
    }
}

package ui.tv.chrome

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.unit.dp
import catalog.ChromeCounts
import dev.chrisbanes.haze.HazeState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ui.common.chrome.FrostKey
import ui.tv.catalog.TvScreenStateTest
import ui.tv.profile.TvChosenProfile
import kotlin.test.assertSame

/**
 * The web's masthead frosts on every page, since something always scrolls or
 * bleeds under it (`shell.css:84-87`); the television's bar does the same on
 * every tab, over a cover (`blend` 0) and on a tab with no hero (`blend` 1).
 * What the bar blurs is the page under it — one shared state, read through
 * [FrostKey] rather than pixels. Below API 31 the chrome hands the bar no
 * frost (`frostAvailable`, tested with the shared code), and a bar without
 * one draws none.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvDepartmentsBarFrostTest : TvScreenStateTest() {
    @Test
    fun overACoverTheBarBlursThePageUnderIt() {
        show { chrome(blend = 0f) }

        assertSame(frostOf(bar()), frostOf(page()))
    }

    @Test
    fun aTabWithNoHeroIsFrostedToo() {
        show { chrome(blend = 1f) }

        assertSame(frostOf(bar()), frostOf(page()))
    }

    @Test
    fun aBarHandedNoFrostDrawsNone() {
        show {
            TvDepartmentsBar(
                pills = DEPARTMENTS, selected = 0, onSelect = {}, onSearch = {}, profile = PROFILE, onMenu = {},
                downTarget = remember { FocusRequester() }, selectedPillFocus = remember { FocusRequester() },
                searchFocus = remember { FocusRequester() }, avatarFocus = remember { FocusRequester() }, frost = null,
            )
        }

        compose.onAllNodes(SemanticsMatcher.keyIsDefined(FrostKey)).assertCountEquals(0)
    }

    private fun bar() = compose.onNode(SemanticsMatcher.keyIsDefined(FrostKey) and hasAnyDescendant(hasText("Movies")))

    private fun page() = compose.onNode(SemanticsMatcher.keyIsDefined(FrostKey) and hasAnyDescendant(hasTestTag(CONTENT)))

    private fun frostOf(node: SemanticsNodeInteraction): HazeState = node.fetchSemanticsNode().config[FrostKey]

    @Composable
    private fun chrome(blend: Float) =
        TvLibraryChrome(
            pills = DEPARTMENTS,
            selectedPill = 0,
            onSelectPill = {},
            railActive = null,
            counts = ChromeCounts(myList = 0, continueWatching = 0, perShelf = emptyMap(), collections = 0),
            tally = emptyList(),
            onRailSelect = {},
            onSearch = {},
            profile = PROFILE,
            onMenu = {},
            focus = rememberTvChromeFocus(),
            blend = blend,
        ) { Box(Modifier.size(200.dp).testTag(CONTENT).focusable()) }

    private companion object {
        val DEPARTMENTS = listOf("Home", "Movies", "Series", "Anime", "Documentaries", "Tutorials", "Collections")
        val PROFILE = TvChosenProfile(name = "Ada", onChoose = {})
        const val CONTENT = "content"
    }
}

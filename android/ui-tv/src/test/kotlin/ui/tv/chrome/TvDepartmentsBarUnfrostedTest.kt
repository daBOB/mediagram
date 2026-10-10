package ui.tv.chrome

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.unit.dp
import catalog.ChromeCounts
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ui.common.chrome.FrostKey
import ui.tv.catalog.TvScreenStateTest
import ui.tv.profile.TvChosenProfile

/**
 * The television's bar is not frosted: the blur measured too slow on the box
 * (`plans/reports/frosted-masthead-261009-report.md`, "TV measurement"). Over
 * a cover (`blend` 0) and on a tab with no hero (`blend` 1) the chrome marks
 * nothing as frost, read through [FrostKey] rather than pixels.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvDepartmentsBarUnfrostedTest : TvScreenStateTest() {
    @Test
    fun overACoverTheBarIsNotFrosted() {
        show { chrome(blend = 0f) }

        compose.onAllNodes(SemanticsMatcher.keyIsDefined(FrostKey)).assertCountEquals(0)
    }

    @Test
    fun aTabWithNoHeroIsNotFrostedEither() {
        show { chrome(blend = 1f) }

        compose.onAllNodes(SemanticsMatcher.keyIsDefined(FrostKey)).assertCountEquals(0)
    }

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

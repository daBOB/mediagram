package ui.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import catalog.CatalogUiState
import catalog.SearchFilter
import catalog.SearchUiState
import catalog.profile.ProfileUiState
import data.PortraitRequestLog
import designsystem.COUNTDOWN_LEADER_TEST_TAG
import designsystem.tvDp
import model.WatchSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import player.PlayerUiState
import ui.tv.catalog.TvGenre
import ui.tv.catalog.TvPersonPage
import ui.tv.catalog.TvScreenStateTest
import ui.tv.catalog.TvSearchResults
import ui.tv.player.TvPlayerStatus
import ui.tv.profile.TvProfilePicker
import ui.tv.setup.TvLibraryChoiceScreen
import kotlin.test.assertEquals

/**
 * Every wait on the television is the film leader, the web's: above its
 * words, at the web's 96px and 24px through the television's scale, and
 * alone on the stage, where only a screen reader hears "Loading". The
 * setup check and the catalogue's own wait are pinned beside their screens
 * (`TvSetupStepTest`, `TvCatalogScreenStateTest`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvLoadingLeaderTest : TvScreenStateTest() {
    @Test
    fun theLibraryLoadingStandsTheLeaderAboveItsWordsInLineWithThem() {
        show { TvLibraryLoading() }

        val drawing = leader().bounds()
        val words = compose.onNodeWithText("Loading your library…").bounds()
        assertDp(96.tvDp, drawing.right - drawing.left)
        assertDp(drawing.left, words.left)
        assertDp(24.tvDp, words.top - drawing.bottom)
    }

    @Test
    fun theProfileReadIsTheLibraryLoading() {
        show { TvProfilePicker(state = ProfileUiState.Loading, onChoose = {}, onStay = {}, onRetry = {}) }

        leader().assertExists()
        compose.onNodeWithText("Loading your library…").assertExists()
    }

    @Test
    fun aWaitInsideAPageCentresTheLeaderOverItsWords() {
        show { TvPersonPage(page = null, loading = true, portrait = null, watch = WatchSnapshot.Empty, heldIds = emptySet(), onOpenTitle = {}, onOpenCollection = {}) }

        val drawing = leader().bounds()
        val words = compose.onNodeWithText("Loading your library…").bounds()
        assertDp(96.tvDp, drawing.right - drawing.left)
        assertDp((drawing.left + drawing.right) / 2, (words.left + words.right) / 2)
        assertDp(24.tvDp, words.top - drawing.bottom)
    }

    @Test
    fun aFrameBeforeTheCatalogueAnswersCountsDown() {
        show { TvResolvedBranch<String>(resolved = null, catalogState = CatalogUiState.Loading, leave = {}) {} }

        leader().assertExists()
        compose.onNodeWithText("Loading your library…").assertExists()
    }

    @Test
    fun aGenreBeforeTheLibraryHasLoadedCountsDown() {
        show { TvGenre("Western", CatalogUiState.Loading, WatchSnapshot.Empty, {}, {}, null) }

        leader().assertExists()
        compose.onNodeWithText("Loading your library…").assertExists()
    }

    @Test
    fun searchBeforeTheLibraryHasLoadedCountsDown() {
        show {
            TvSearchResults(
                state = SearchUiState.Ready(hits = emptyList()),
                sections = emptyList(),
                filters = emptyList(),
                filter = SearchFilter.ALL,
                onFilterChange = {},
                catalogReady = false,
                watch = WatchSnapshot.Empty,
                ask = null,
                onAnswered = {},
                onPlay = {},
                onOpenTitle = {},
                onOpenCollection = {},
                onOpenPerson = {},
                onOpenDestination = {},
                portraits = PortraitRequestLog(),
                fetchPortrait = { null },
            )
        }

        leader().assertExists()
        compose.onNodeWithText("Loading your library…").assertExists()
    }

    @Test
    fun readingTheAccountsChannelsCountsDownInTheWebsWords() {
        show { TvLibraryChoiceScreen(choices = null, error = null, onChoose = {}, onLookAgain = {}) }

        leader().assertExists()
        compose.onNodeWithText("Reading the account's channels…").assertExists()
    }

    @Test
    fun theStageWhileATitleOpensIsTheLeaderAloneAndSaysLoading() {
        val retry = FocusRequester()
        show { TvPlayerStatus(state = PlayerUiState.Preparing, retry = retry, onRetry = {}, onReport = {}) }

        val drawing = leader().bounds()
        assertDp(96.tvDp, drawing.right - drawing.left)
        compose.onNodeWithContentDescription("Loading").assertExists()
        compose.onNodeWithText("Loading", substring = true).assertDoesNotExist()
    }

    @Test
    fun oneCountRunsThroughTheColdStartsWaitsButAPageWaitStartsAtThree() {
        var wait by mutableIntStateOf(0)
        compose.mainClock.autoAdvance = false
        show {
            TvColdStart {
                when (wait) {
                    0 -> TvLibraryLoading()
                    1 -> TvProfilePicker(state = ProfileUiState.Loading, onChoose = {}, onStay = {}, onRetry = {})
                    else -> TvCentredLoading("Reading the account's channels…")
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()

        compose.mainClock.advanceTimeBy(1100)
        assertEquals(2, numeral())
        compose.runOnUiThread { wait = 1 }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(2, numeral())
        compose.runOnUiThread { wait = 2 }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(3, numeral())
    }

    /** The leader's figure, read by name: its key is the design system's own, not visible from here. */
    private fun numeral(): Int {
        compose.waitForIdle()
        val config = leader().fetchSemanticsNode().config
        return config.single { it.key.name == "CountdownNumeral" }.value as Int
    }

    private fun leader() = compose.onNodeWithTag(COUNTDOWN_LEADER_TEST_TAG)

    private fun SemanticsNodeInteraction.bounds(): DpRect = getUnclippedBoundsInRoot()

    /** Within half a dp: bounds come back through pixels. */
    private fun assertDp(
        expected: Dp,
        actual: Dp,
    ) = assertEquals(expected.value, actual.value, 0.5f)
}

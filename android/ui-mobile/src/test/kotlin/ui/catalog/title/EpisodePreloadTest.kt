package ui.catalog.title

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import catalog.CollectionKind
import catalog.Division
import catalog.Entry
import model.Kind
import model.MediaSet
import model.WatchSnapshot
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import playback.FilmPreloadState
import ui.catalog.CollectionScreen
import ui.common.catalog.TitlePreloadUi
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An episode row's own Preload control and held share, drawn from plain
 * [TitlePreloadUi] values the way a film page's tests build theirs — the
 * engine and its words are the film's; what is checked here is only what
 * the row shows for each state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp")
class EpisodePreloadTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private val episode =
        MediaSet(
            setId = "s1e1", kind = Kind.EPISODE, title = "Pilot", show = "Show",
            chapter = null, path = null, season = 1, episodeFirst = 1, episodeLast = null,
            year = 2020, durationSecs = 1800, posterPath = null, totalBytes = TOTAL,
        )

    private val show =
        Entry.Collection(
            key = "series/Show", kind = CollectionKind.SHOW, name = "Show", posterPath = null, posterKey = "tmdb-tv-1",
            count = 1, chapters = 1, divisions = listOf(Division("Season 1", 1, listOf(episode), emptyList())),
        )

    private fun render(ui: TitlePreloadUi, heldIds: Set<String> = emptySet()) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MaterialTheme {
                    CollectionScreen(
                        collection = show, info = null, watch = WatchSnapshot.Empty, heldIds = heldIds,
                        onOpenTitle = {}, onOpenGenre = {},
                        episodePreload = { set: MediaSet -> ui.takeIf { set.setId == episode.setId } },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun ui(state: FilmPreloadState, held: Long = 0L, onToggle: () -> Unit = {}, onRemove: () -> Unit = {}, queuedAhead: String? = null, budget: Long? = null) =
        TitlePreloadUi(
            state = state, serverLine = null, onToggle = onToggle, onRemove = onRemove, onOpenStorage = {},
            queuedAheadLabel = queuedAhead, needsSpaceBudgetBytes = budget, heldBytes = held,
        )

    private fun anyPercentShown(): Boolean = compose.onAllNodesWithText("%", substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun anEpisodeRowOffersPreload() {
        var toggled = 0
        render(ui(FilmPreloadState.Idle(0L, TOTAL), onToggle = { toggled++ }))

        compose.onNode(hasText("Preload · 100 MB") and hasClickAction()).performClick()

        assertEquals(1, toggled)
    }

    @Test
    fun aPartlyHeldEpisodeShowsItsPercentage() {
        render(ui(FilmPreloadState.Running(HELD_42, TOTAL), held = HELD_42))

        compose.onNodeWithText("42%").assertExists()
        // Said in full to a screen reader, as a film page's bar says it.
        compose.onNodeWithContentDescription("42 of 100 MB · 42%").assertExists()
        compose.onNodeWithText("Preloading").assertExists()
    }

    /** Queued carries no bytes of its own, and the label names another episode — the figure beside it is still this one's. */
    @Test
    fun aQueuedPartlyHeldEpisodeShowsItsOwnPercentage() {
        render(ui(FilmPreloadState.Queued, held = HELD_42, queuedAhead = "Queued · after Show · S1E2"))

        compose.onNodeWithText("42%").assertExists()
        compose.onNodeWithText("Queued · after Show · S1E2").assertExists()
    }

    @Test
    fun aFullyHeldEpisodeShowsTheHeldBadge() {
        render(ui(FilmPreloadState.Done, held = TOTAL))

        compose.onNodeWithText("offline").assertExists()
        compose.onNodeWithText("Preloaded ✓").assertExists()
        assertTrue(!anyPercentShown(), "no percentage beside a held badge")
    }

    /** Held in full by watching alone, never preloaded: the same badge. */
    @Test
    fun anEpisodeFilledByWatchingShowsTheHeldBadge() {
        render(ui(FilmPreloadState.Idle(TOTAL, TOTAL), held = TOTAL))

        compose.onNodeWithText("offline").assertExists()
        assertTrue(!anyPercentShown(), "no percentage beside a held badge")
    }

    @Test
    fun aPreloadedEpisodeCanBeRemoved() {
        var removed = 0
        render(ui(FilmPreloadState.Done, held = TOTAL, onRemove = { removed++ }))

        compose.onNode(hasText("Preloaded ✓") and hasClickAction()).performClick()
        compose.onNodeWithText("Remove preload").performClick()

        assertEquals(1, removed)
    }

    @Test
    fun nothingShowsForAnEpisodeWithNothingHeld() {
        render(ui(FilmPreloadState.Idle(0L, TOTAL)))

        assertTrue(!anyPercentShown(), "no percentage at 0%")
        compose.onNodeWithText("offline").assertDoesNotExist()
    }

    /**
     * The longest state a row can be in, on a narrow phone: the control stays
     * small and the title keeps its room. Native graphics, so text is measured
     * as a device measures it rather than near zero.
     */
    @Test
    @Config(qualifiers = "w360dp-h740dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aLongStateLeavesTheTitleRoomOnAPhone() {
        render(ui(FilmPreloadState.NeedsSpace(TOTAL), budget = 8L shl 30))

        compose.onNodeWithText("1. Pilot").assertIsDisplayed().assertWidthIsAtLeast(120.dp)
        compose.onNodeWithText("Needs 100 MB · budget is 8.0 GB · Try again").assertExists()
    }

    private companion object {
        const val TOTAL = 100 * 1_048_576L
        const val HELD_42 = TOTAL * 42 / 100
    }
}

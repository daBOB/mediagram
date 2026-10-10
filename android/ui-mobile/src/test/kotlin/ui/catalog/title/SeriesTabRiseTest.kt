package ui.catalog.title

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import ui.catalog.CollectionScreen
import uniffi.mediagram_core.TitleInfo
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The series page's tab panel rises 8px as one block each time its tab is
 * shown (`title-page.css:133-134`, `series-page.js:101`). Rows that arrive
 * with it rise together; rows scrolled to later are already in, and nothing
 * rises again on a scroll back. Lazy list: spread 0, tab row 1, season
 * heading 2, then the episodes, so "31. Ep-031" is item 33.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h1600dp")
class SeriesTabRiseTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { MaterialTheme { content() } }
        }
        frames(2)
    }

    private fun frames(count: Int) = repeat(count) { compose.mainClock.advanceTimeByFrame() }

    // Stepped, not one jump: a rise has to start before time can carry it.
    private fun settle() = repeat(4) { compose.mainClock.advanceTimeBy(300) }

    private fun top(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot().top.value

    private val series =
        Entry.Collection(
            key = "series/Show", kind = CollectionKind.SHOW, name = "Show", posterPath = null, posterKey = "tmdb-tv-1",
            count = 40, chapters = 1,
            divisions =
                listOf(
                    Division(
                        "Season 1", 1,
                        (1..40).map { n ->
                            MediaSet(
                                setId = "s1e$n", kind = Kind.EPISODE, title = "Ep-%03d".format(n), show = "Show", chapter = null,
                                path = null, season = 1, episodeFirst = n, episodeLast = null, year = 2020, durationSecs = 1800,
                                posterPath = null, totalBytes = 10, backdropPath = null,
                            )
                        },
                        emptyList(),
                    ),
                ),
        )

    private val info = TitleInfo(overview = "Spice.", tagline = null, genres = null, rating = null, network = null, status = null)

    private fun page(list: LazyListState = LazyListState()) =
        show {
            CollectionScreen(
                collection = series, info = info, watch = WatchSnapshot.Empty, heldIds = emptySet(),
                onOpenTitle = {}, onOpenGenre = {}, state = list,
            )
        }

    @Test fun theEpisodesPanelRisesAsOneAndRowsScrolledToLaterAreAlreadyIn() {
        val list = LazyListState()
        page(list)
        val first = top("1. Ep-001")
        val second = top("2. Ep-002")
        compose.mainClock.advanceTimeBy(600)
        assertTrue(first > top("1. Ep-001") + 5f && second > top("2. Ep-002") + 5f, "the panel's rows rise together")

        compose.runOnUiThread { list.requestScrollToItem(33) }
        frames(2)
        val arrived = top("31. Ep-031")
        compose.mainClock.advanceTimeBy(600)
        assertEquals(arrived, top("31. Ep-031"), 0.5f, "a row scrolled to later is already in")

        compose.runOnUiThread { list.requestScrollToItem(0) }
        frames(2)
        val back = top("1. Ep-001")
        compose.mainClock.advanceTimeBy(600)
        assertEquals(back, top("1. Ep-001"), 0.5f, "scrolled back, nothing rises again")
    }

    @Test fun everySwitchToATabRisesItsPanelAgain() {
        page()
        top("1. Ep-001") // reading syncs the first placement, which is what starts the rise
        settle()
        val rest = top("1. Ep-001")
        compose.onNodeWithText("About").performClick()
        frames(2)
        settle()
        compose.onNodeWithText("Episodes").performClick()
        compose.waitForIdle()
        frames(2)
        assertTrue(top("1. Ep-001") > rest + 5f, "back on Episodes, its panel rises again")
        settle()
        assertEquals(rest, top("1. Ep-001"), 0.5f)
    }
}

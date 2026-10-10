package ui.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.IntSize
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
import ui.common.catalog.HERO_ARTWORK_TEST_TAG
import uniffi.mediagram_core.TitleInfo
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The hero art-in plays once a page visit, Back included, as the web's does,
 * and never on a scroll back, a redraw or a turn of the tablet. A picture
 * still settling from 1.04 has its top-left further left than at rest, and a
 * node's unclipped bounds in root follow the layer, so that is what is read.
 * Alpha cannot be read here (see PageGroundColorTest); the fade is the device
 * walk's. Native graphics give text its real width, so a quote's scale shows.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp")
class HeroArtInTest {
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

    private fun frames(count: Int) {
        compose.waitForIdle()
        repeat(count) { compose.mainClock.advanceTimeByFrame() }
    }

    private fun artLeft() =
        compose.onNodeWithTag(HERO_ARTWORK_TEST_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot().left.value

    private fun leftOf(text: String) =
        compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot().left.value

    private val film =
        MediaSet(
            setId = "film-1", kind = Kind.MOVIE, title = "Dune", show = null, chapter = null, path = null,
            season = null, episodeFirst = null, episodeLast = null, year = 2021, durationSecs = 9000,
            posterPath = null, totalBytes = 10, backdropPath = "backdrop.jpg",
        )

    private val info = TitleInfo(overview = "Spice.", tagline = "A beginning", genres = null, rating = null, network = null, status = null)

    private val lead =
        MediaSet(
            setId = "lead", kind = Kind.MOVIE, title = "a lowercase title", show = null, chapter = null, path = null,
            season = null, episodeFirst = null, episodeLast = null, year = 2020, durationSecs = 6_600, posterPath = null,
            totalBytes = 0, backdropPath = "backdrop.jpg", tagline = "a quotable line",
        )

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
                                posterPath = null, totalBytes = 10, backdropPath = "backdrop.jpg",
                            )
                        },
                        emptyList(),
                    ),
                ),
        )

    @Composable
    private fun Department(reveals: Reveals) =
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { DepartmentHero(title = "Movies", line = "four films", lead = lead, onOpenTitle = null, reveals = reveals) }
            // Enough rows of the same content type that the hero's slot is reused, not kept.
            items(12) { Spacer(Modifier.fillMaxWidth().height(400.dp)) }
        }

    @Test fun aTitlesArtSettlesFromAboveRestAndARedrawLeavesItThere() {
        var pinned by mutableStateOf<String?>(null)
        show { TitleDetailScreen(film, info, {}, onOpenGenre = {}, editorsChoice = pinned) }
        val start = artLeft()
        compose.mainClock.advanceTimeBy(1_600)
        val rest = artLeft()
        assertTrue(start < rest - 5f, "the art starts at 1.04: left $start against $rest at rest")
        compose.runOnUiThread { pinned = film.setId }
        frames(2)
        assertEquals(rest, artLeft(), 0.5f, "a redraw of the same page never replays it")
    }

    @Test fun theQuoteWaits200msBehindItsPicture() {
        show { TitleDetailScreen(film, info, {}, onOpenGenre = {}) }
        val art = artLeft()
        val quote = leftOf("“A beginning”")
        compose.mainClock.advanceTimeBy(150)
        assertTrue(artLeft() > art + 1f, "the picture is already settling")
        assertEquals(quote, leftOf("“A beginning”"), 0.05f, "the quote is still waiting")
        compose.mainClock.advanceTimeBy(1_700)
        assertTrue(leftOf("“A beginning”") > quote + 1f, "and then it settles too")
    }

    @Test fun aNewlyOpenedTitlePlaysIt() {
        var visit by mutableStateOf(0)
        show { key(visit) { TitleDetailScreen(film, info, {}, onOpenGenre = {}) } }
        compose.mainClock.advanceTimeBy(1_600)
        val rest = artLeft()
        compose.runOnUiThread { visit = 1 }
        frames(2)
        assertTrue(artLeft() < rest - 5f, "a new frame key is a new visit, and it plays")
    }

    /** The frame's saved slot outlives a page pushed over it, as `keyedFrame`'s does; the art-in must not. */
    @Test fun aTitleComeBackToWithBackPlaysItAgain() {
        var open by mutableStateOf(true)
        show {
            val held = rememberSaveableStateHolder()
            if (open) held.SaveableStateProvider("title") { TitleDetailScreen(film, info, {}, onOpenGenre = {}) }
        }
        compose.mainClock.advanceTimeBy(1_600)
        val rest = artLeft()
        compose.runOnUiThread { open = false }
        frames(2)
        compose.runOnUiThread { open = true }
        frames(2)
        assertTrue(artLeft() < rest - 5f, "Back is a new visit, and plays it, as on the web")
    }

    /** A fake [WindowInfo] flips the width class in place, as a real rotation does here (WidthClassStateTest). */
    @Test fun turningTheTabletAcrossTheWideBreakpointDoesNotReplayIt() {
        var widthPx by mutableStateOf(1280)
        show {
            val real = LocalWindowInfo.current
            val turned = object : WindowInfo by real { override val containerSize: IntSize get() = IntSize(widthPx, 800) }
            CompositionLocalProvider(LocalWindowInfo provides turned) { TitleDetailScreen(film, info, {}, onOpenGenre = {}) }
        }
        compose.mainClock.advanceTimeBy(1_600)
        compose.onNodeWithTag(WIDE_TITLE_SPREAD_TEST_TAG, useUnmergedTree = true).assertExists()
        compose.runOnUiThread { widthPx = 800 }
        frames(2)
        compose.onNodeWithTag(WIDE_TITLE_SPREAD_TEST_TAG, useUnmergedTree = true).assertDoesNotExist()
        val arrived = artLeft()
        compose.mainClock.advanceTimeBy(1_600)
        assertEquals(arrived, artLeft(), 0.5f, "the stacked spread's art arrives at rest")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h800dp")
    fun aPhonesStackedSpreadSettlesToo() {
        show { TitleDetailScreen(film, info, {}, onOpenGenre = {}) }
        val start = artLeft()
        compose.mainClock.advanceTimeBy(1_600)
        assertTrue(start < artLeft() - 3f, "the stacked spread's art starts at 1.04 too")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h1600dp")
    fun aSeriesSpreadScrolledAwayAndBackStaysAtRest() {
        val list = LazyListState()
        show { CollectionScreen(collection = series, info = info, watch = WatchSnapshot.Empty, heldIds = emptySet(), onOpenTitle = {}, onOpenGenre = {}, state = list) }
        val start = artLeft()
        compose.mainClock.advanceTimeBy(1_600)
        val rest = artLeft()
        assertTrue(start < rest - 3f, "the series spread's art starts at 1.04")
        compose.runOnUiThread { list.requestScrollToItem(40) }
        frames(3)
        compose.runOnUiThread { list.requestScrollToItem(0) }
        frames(2)
        assertEquals(rest, artLeft(), 0.5f, "scrolled back, the picture is already at rest")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1164dp-h777dp")
    fun aDepartmentHeroScrolledAwayAndBackStaysAtRest() {
        val list = LazyListState()
        show {
            val reveals = rememberReveals()
            LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                item { DepartmentHero(title = "Movies", line = "four films", lead = lead, onOpenTitle = null, reveals = reveals) }
                items(12) { Spacer(Modifier.fillMaxWidth().height(400.dp)) }
            }
        }
        val art = artLeft()
        val quote = leftOf("“a quotable line”")
        compose.mainClock.advanceTimeBy(150)
        assertEquals(quote, leftOf("“a quotable line”"), 0.05f, "the quote waits behind its picture")
        compose.mainClock.advanceTimeBy(1_700)
        val artRest = artLeft()
        val quoteRest = leftOf("“a quotable line”")
        assertTrue(art < artRest - 5f && quote < quoteRest - 1f, "both settle from 1.04")
        compose.runOnUiThread { list.requestScrollToItem(12) }
        frames(3)
        compose.runOnUiThread { list.requestScrollToItem(0) }
        frames(2)
        assertEquals(artRest, artLeft(), 0.5f, "scrolled back, the picture is already at rest")
        assertEquals(quoteRest, leftOf("“a quotable line”"), 0.5f, "and so is its quote")
    }

    /** The shelves' saved slot outlives a title pushed over them; the department's art-in must not. */
    @Test
    @Config(sdk = [35], qualifiers = "w1164dp-h777dp")
    fun aDepartmentComeBackToWithBackPlaysItAgain() {
        var open by mutableStateOf(true)
        show {
            val held = rememberSaveableStateHolder()
            if (open) held.SaveableStateProvider("shelves") { Department(rememberReveals()) }
        }
        compose.mainClock.advanceTimeBy(1_800)
        val rest = artLeft()
        compose.runOnUiThread { open = false }
        frames(2)
        compose.runOnUiThread { open = true }
        frames(2)
        assertTrue(artLeft() < rest - 5f, "Back is a new visit, and plays it, as on the web")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h800dp")
    fun aPhonesDepartmentHeroSettlesToo() {
        show { Department(rememberReveals()) }
        val start = artLeft()
        compose.mainClock.advanceTimeBy(1_600)
        assertTrue(start < artLeft() - 3f, "the compact hero's strip starts at 1.04 too")
    }
}

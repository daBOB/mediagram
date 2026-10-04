package ui.catalog.title

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
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
import ui.catalog.HERO_ARTWORK_TEST_TAG
import ui.catalog.TitleDetailScreen
import ui.catalog.WIDE_TITLE_SPREAD_TEST_TAG
import uniffi.mediagram_core.TitleInfo
import kotlin.math.abs
import kotlin.test.assertTrue

/**
 * A tablet in landscape draws the web's own wide spread (`title-page.css`
 * above 900px): the art from 28% across to the right edge, the words and
 * pills bottom-left over its fade, the tagline bottom-right over the
 * picture. A tablet in portrait keeps the phone's stacked spread.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp")
class WideTitleSpreadTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { MaterialTheme { content() } }
        }
        compose.waitForIdle()
    }

    private val film =
        MediaSet(
            setId = "film-1", kind = Kind.MOVIE, title = "Dune", show = null, chapter = null, path = null,
            season = null, episodeFirst = null, episodeLast = null, year = 2021, durationSecs = 9000,
            posterPath = null, totalBytes = 10, backdropPath = "backdrop.jpg",
        )

    private val episode =
        MediaSet(
            setId = "s1e1", kind = Kind.EPISODE, title = "Ep 1.1", show = "Show", chapter = null, path = null,
            season = 1, episodeFirst = 1, episodeLast = null, year = 2020, durationSecs = 1800,
            posterPath = null, totalBytes = 10, backdropPath = "backdrop.jpg",
        )

    private val series =
        Entry.Collection(
            key = "series/Show", kind = CollectionKind.SHOW, name = "Show", posterPath = null, posterKey = "tmdb-tv-1",
            count = 1, chapters = 1, divisions = listOf(Division("Season 1", 1, listOf(episode), emptyList())),
        )

    private val info = TitleInfo(overview = "Spice.", tagline = "A beginning", genres = null, rating = null, network = null, status = null)

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()

    private fun DpRect.contains(inner: DpRect) =
        inner.left >= left && inner.right <= right && inner.top >= top && inner.bottom <= bottom

    @Test fun aFilmsArtFillsTheSpreadFromTwentyEightPercentAcross() {
        show { TitleDetailScreen(film, info, {}, onOpenGenre = {}) }
        val spread = bounds(WIDE_TITLE_SPREAD_TEST_TAG)
        val art = bounds(HERO_ARTWORK_TEST_TAG)
        assertTrue(abs((art.left - spread.left).value - spread.width.value * 0.28f) < 1f, "art starts at $art in $spread")
        assertTrue(abs((art.right - spread.right).value) < 1f)
        assertTrue(abs((art.top - spread.top).value) < 1f && abs((art.bottom - spread.bottom).value) < 1f)
    }

    @Test fun aFilmsWordsAndPillsSitBottomLeftAndItsQuoteBottomRight() {
        show { TitleDetailScreen(film, info, {}, onOpenGenre = {}) }
        val spread = bounds(WIDE_TITLE_SPREAD_TEST_TAG)
        val middle = spread.left + spread.width / 2
        val play = compose.onNodeWithText("▶ Play").assertIsDisplayed().getBoundsInRoot()
        assertTrue(spread.contains(play) && play.right < middle && play.top > spread.top + spread.height / 2, "pill at $play in $spread")
        val title = compose.onNodeWithText("Dune").getBoundsInRoot()
        assertTrue(title.left < middle && title.bottom < play.top)
        val quote = compose.onNodeWithText("“A beginning”").assertIsDisplayed().getBoundsInRoot()
        assertTrue(spread.contains(quote) && quote.left > middle && quote.bottom < spread.bottom, "quote at $quote in $spread")
    }

    /** The series page lays its spread in a padded list; the wide spread reaches back over that margin to the window's edges. */
    @Test fun aSeriesSpreadMeetsTheWindowsEdgesWithItsPillsInside() {
        show {
            CollectionScreen(
                collection = series, info = info, watch = WatchSnapshot.Empty, heldIds = emptySet(),
                onOpenTitle = {}, onOpenSeason = {}, onOpenGenre = {},
            )
        }
        val root = compose.onRoot().getBoundsInRoot()
        val spread = bounds(WIDE_TITLE_SPREAD_TEST_TAG)
        assertTrue(abs((spread.left - root.left).value) < 1f && abs((spread.right - root.right).value) < 1f, "spread at $spread in $root")
        val play = compose.onNodeWithText("▶ Play S1 E1").assertIsDisplayed().getBoundsInRoot()
        assertTrue(spread.contains(play))
        compose.onNodeWithText("“A beginning”").assertIsDisplayed()
    }

    /** Portrait is the compact header's width: the stacked spread, and no quote — the web's ≤900px rule. */
    @Test
    @Config(sdk = [35], qualifiers = "w800dp-h1280dp")
    fun aTabletInPortraitKeepsTheStackedSpread() {
        show { TitleDetailScreen(film, info, {}, onOpenGenre = {}) }
        compose.onNodeWithTag(WIDE_TITLE_SPREAD_TEST_TAG, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("“A beginning”").assertDoesNotExist()
        compose.onNodeWithText("▶ Play").assertIsDisplayed()
    }
}

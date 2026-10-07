package ui.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import catalog.CatalogTab
import catalog.CollectionKind
import catalog.EditorialPicks
import catalog.Entry
import catalog.Feature
import catalog.FeatureKind
import catalog.KeptKind
import catalog.Latest
import catalog.MagazineHome
import catalog.SetCard
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
import ui.catalog.home.HOME_COVER_TEST_TAG
import ui.catalog.home.HOME_FEATURES_TEST_TAG
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The assembled home page: every section still appears in the web's own
 * order (cover, features, Continue, Recently Added, Latest series, Latest
 * courses), and "See all" beside Continue Watching lands on the Continue
 * tab specifically, not on whichever shelf a viewer tapped nearest.
 *
 * A tall window (`h4000dp`, at the tablet's own 1164dp width, where the
 * cover clamps to its own 705dp ceiling rather than growing with it) so
 * every section realizes in the `LazyColumn` at once — the same reason
 * `HeroBackdropModesTest` picks its own tall window.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1164dp-h4000dp")
class HomeScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun film(
        id: String,
        tagline: String? = null,
    ) = MediaSet(
        setId = id, kind = Kind.MOVIE, title = "Film ${id.uppercase()}", show = null, chapter = null, path = null,
        season = null, episodeFirst = null, episodeLast = null, year = 2021, durationSecs = 3_600, posterPath = "p-$id.jpg",
        totalBytes = 0, backdropPath = "b-$id.jpg", tagline = tagline,
    )

    private val magazine =
        MagazineHome(
            editorial =
                EditorialPicks(
                    cover = listOf(film("cover")),
                    features = listOf(Feature(FeatureKind.EDITOR, film("feature"))),
                    quote = film("quote", tagline = "A quotable line."),
                    thisMonth = listOf(film("month")),
                ),
            resumeCards = listOf(SetCard(film("resume"), "1h left", 0.4f, false, false)),
            recentlyAdded = listOf(film("recent")),
            recentlyAddedTotal = 1,
        )
    private val latest =
        Latest(
            movies = emptyList(),
            series = listOf(Entry.Collection("s1", CollectionKind.SHOW, "Show One", null, null, count = 10, chapters = 2, divisions = emptyList())),
            courses = listOf(Entry.Collection("c1", CollectionKind.COURSE, "Course One", null, null, count = 8, chapters = 1, divisions = emptyList())),
            moviesTotal = 0,
            seriesTotal = 1,
            coursesTotal = 1,
        )

    private fun show(onSeeAll: (CatalogTab) -> Unit = {}) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MaterialTheme {
                    HomeScreen(
                        magazine = magazine, latest = latest, watch = WatchSnapshot.Empty, listState = rememberLazyListState(),
                        onPlay = {}, onOpenTitle = {}, onOpenCollection = {}, onToggleWatchlist = { _, _ -> }, onSeeAll = onSeeAll,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun everySectionAppearsInTheWebsOwnOrder() {
        show()
        val tops =
            listOf("FILM COVER", "FILM FEATURE", "Continue Watching", "Recently Added", "Latest series", "Latest courses")
                .map { compose.onNodeWithText(it).getUnclippedBoundsInRoot().top.value }
        assertTrue(tops.zipWithNext().all { (a, b) -> a < b }, "sections should read top to bottom in the web's own order: $tops")
    }

    @Test
    fun theFeaturesBlockStartsAtOrBelowTheCoversOwnBottom() {
        // Guards against the cover drawing over its own `LazyColumn` item
        // slot (an unclipped child, or a slot measured taller than the
        // exact height `HomeCover` itself asks for) and against the
        // features item collapsing to zero — either would let whatever
        // comes after read as sitting directly under the cover.
        show()
        val coverBottom = compose.onNodeWithTag(HOME_COVER_TEST_TAG).getUnclippedBoundsInRoot().bottom
        val featuresTop = compose.onNodeWithTag(HOME_FEATURES_TEST_TAG).getUnclippedBoundsInRoot().top
        assertTrue(featuresTop >= coverBottom, "features ($featuresTop) should start at or below the cover's own bottom ($coverBottom)")
    }

    @Test
    fun seeAllBesideContinueWatchingLandsOnTheContinueTab() {
        var target: CatalogTab? = null
        show(onSeeAll = { target = it })
        // Continue's own band is the first "See all" in the page's own
        // order — Recently Added, Latest series and Latest courses each
        // carry one too, so the plain text alone is ambiguous.
        compose.onAllNodesWithText("See all →")[0].performClick()
        assertEquals(CatalogTab.Kept(KeptKind.CONTINUE), target)
    }
}

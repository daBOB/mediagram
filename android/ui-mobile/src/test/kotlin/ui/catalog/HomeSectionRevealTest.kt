package ui.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import catalog.CollectionKind
import catalog.EditorialPicks
import catalog.Entry
import catalog.Feature
import catalog.FeatureKind
import catalog.Latest
import catalog.MagazineHome
import catalog.SetCard
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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

/**
 * Home's sections rise 28dp into place the first time each is placed on a
 * visit, and never again on that visit: not when flung away and back, not
 * when a sync redraws them with the same keys. The cover carries none.
 *
 * A phone window, where the cover fills most of the first screen and the
 * features start just under it. The features block rests 12dp below the
 * cover's foot (its own top padding in HomeScreen), so its offset from rest
 * is read against the cover rather than a remembered number.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class HomeSectionRevealTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    private val list = LazyListState()

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun film(id: String, tagline: String? = null) = MediaSet(
        setId = id, kind = Kind.MOVIE, title = "Film ${id.uppercase()}", show = null, chapter = null, path = null,
        season = null, episodeFirst = null, episodeLast = null, year = 2021, durationSecs = 3_600, posterPath = "p-$id.jpg",
        totalBytes = 0, backdropPath = "b-$id.jpg", tagline = tagline,
    )

    private val magazine = MagazineHome(
        editorial = EditorialPicks(
            cover = listOf(film("cover")),
            features = listOf(Feature(FeatureKind.EDITOR, film("feature"))),
            quote = film("quote", tagline = "A quotable line."),
            thisMonth = listOf(film("month")),
        ),
        resumeCards = listOf(SetCard(film("resume"), "1h left", 0.4f, false, false)),
        recentlyAdded = listOf(film("recent")),
        recentlyAddedTotal = 1,
    )
    private val latest = Latest(
        movies = emptyList(),
        series = listOf(Entry.Collection("s1", CollectionKind.SHOW, "Show One", null, null, count = 10, chapters = 2, divisions = emptyList())),
        courses = listOf(Entry.Collection("c1", CollectionKind.COURSE, "Course One", null, null, count = 8, chapters = 1, divisions = emptyList())),
        moviesTotal = 0,
        seriesTotal = 1,
        coursesTotal = 1,
    )
    private val shown = mutableStateOf(magazine)

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MaterialTheme {
                    HomeScreen(
                        magazine = shown.value, latest = latest, watch = WatchSnapshot.Empty, listState = list,
                        onPlay = {}, onOpenTitle = {}, onOpenCollection = {}, onToggleWatchlist = { _, _ -> }, onSeeAll = {},
                    )
                }
            }
        }
        twoFrames()
    }

    private fun twoFrames() {
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
    }

    /** The placement has to be delivered (idle) before the clock runs, or the animation it starts begins after the advance. */
    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_600)
    }

    private fun scrollTo(index: Int) {
        compose.runOnUiThread { list.requestScrollToItem(index) }
        twoFrames()
    }

    private fun coverTop() = compose.onNodeWithTag(HOME_COVER_TEST_TAG).getUnclippedBoundsInRoot().top.value
    private fun coverBottom() = compose.onNodeWithTag(HOME_COVER_TEST_TAG).getUnclippedBoundsInRoot().bottom.value
    private fun featuresTop() = compose.onNodeWithTag(HOME_FEATURES_TEST_TAG).getUnclippedBoundsInRoot().top.value
    private fun topOf(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot().top.value

    /** How far the features block sits below its rest, 12dp under the cover. */
    private fun featuresOffset() = featuresTop() - (coverBottom() + 12f)

    @Test
    fun aSectionRisesAsItArrivesButTheCoverDoesNot() {
        show()
        assertEquals(0f, coverTop(), 0.5f)
        assertTrue(featuresOffset() > 20f, "the features should start well below their place, were ${featuresOffset()}dp")
        compose.mainClock.advanceTimeBy(1_200)
        assertEquals(0f, featuresOffset(), 0.5f)
    }

    @Test
    fun aSectionScrolledToLaterRisesWhenItArrivesNotWhenItWasComposed() {
        show()
        settle()
        scrollTo(1)
        val arriving = topOf("Continue Watching")
        compose.mainClock.advanceTimeBy(1_200)
        assertTrue(arriving - topOf("Continue Watching") > 20f, "Continue should rise as it arrives")
    }

    /** A fast fling down Home and back up: the section left mid-rise is at rest on the way back, neither replayed nor stuck. */
    @Test
    fun aFlingDownAndBackLeavesTheSectionsAtRest() {
        show()
        compose.mainClock.advanceTimeBy(200)
        assertTrue(featuresOffset() > 5f, "still rising when flung away")
        scrollTo(5)
        compose.onNodeWithTag(HOME_FEATURES_TEST_TAG).assertDoesNotExist()
        scrollTo(0)
        assertEquals(0f, featuresOffset(), 0.5f)
    }

    /** A sync that lands with the same sections redraws them where they are. */
    @Test
    @Config(qualifiers = "w1164dp-h4000dp")
    fun aSyncThatKeepsTheSectionsReplaysNone() {
        show()
        settle()
        val before = listOf(featuresTop(), topOf("Continue Watching"), topOf("Latest series"))
        compose.runOnUiThread {
            shown.value = magazine.copy(editorial = magazine.editorial.copy(quote = film("quote", tagline = "Another line.")))
        }
        compose.waitForIdle()
        twoFrames()
        compose.onNodeWithText("Another line.", substring = true, useUnmergedTree = true).assertExists()
        val after = listOf(featuresTop(), topOf("Continue Watching"), topOf("Latest series"))
        before.zip(after).forEach { (was, now) -> assertEquals(was, now, 0.5f) }
    }
}

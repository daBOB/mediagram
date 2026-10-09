package ui.tv.catalog

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import catalog.CollectionKind
import catalog.Division
import catalog.EditorialPicks
import catalog.Entry
import catalog.Latest
import catalog.MagazineHome
import catalog.TogetherOffer
import model.Kind
import model.MediaSet
import model.WatchSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.common.catalog.LocalTogetherJoin
import ui.common.catalog.TogetherJoin
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Join on the television, as `together-join.js` offers it on the web: a
 * focusable plate under Home's cover, which takes the remote as Home appears,
 * and Join in place of Play on the page of the title the session plays. A
 * session heard or ended while Home is up never throws the remote to the bar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvTogetherJoinTest : TvScreenStateTest() {
    private var joined = 0
    private val shared = set("shared", Kind.MOVIE, "Shared Film", addedAt = 0)
    private val coverFilm = set("cover", Kind.MOVIE, "Cover Film", addedAt = 0)
    private val plate = hasText("Watching together: Shared Film · TV, Tablet")
    private val watchNow = hasContentDescription("Watch now: Cover Film")

    /** The cover's own stops: Watch now, My List and Details. */
    private val inCover = watchNow or hasText("My List", substring = true) or hasText("Details")

    private fun joining(set: MediaSet) = TogetherJoin(TogetherOffer(41, set, "TV, Tablet")) { joined++ }

    private fun ok() = compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionCenter) }

    private fun press(key: Key) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun home(together: () -> TogetherJoin?) =
        show {
            CompositionLocalProvider(LocalTogetherJoin provides together()) {
                TvHome(
                    magazine =
                        MagazineHome(
                            editorial = EditorialPicks(cover = listOf(coverFilm), features = emptyList(), quote = null, thisMonth = emptyList()),
                            resumeCards = emptyList(),
                            recentlyAdded = emptyList(),
                            recentlyAddedTotal = 0,
                        ),
                    latest = Latest(emptyList(), emptyList(), emptyList(), 0, 0, 0),
                    watch = WatchSnapshot.Empty,
                    listState = rememberLazyListState(),
                    onPlay = {},
                    onOpenTitle = {},
                    onOpenCollection = {},
                    onToggleWatchlist = { _, _ -> },
                    onSeeAll = {},
                    upExit = remember { FocusRequester() },
                )
            }
        }

    @Test
    fun theSessionsPlateSitsUnderTheCoverTakesTheRemoteAndJoins() {
        home { joining(shared) }

        compose.onNode(plate).assertIsFocused()
        val plateTop = compose.onNode(plate).getUnclippedBoundsInRoot().top
        assertTrue(plateTop >= compose.onNode(watchNow).getUnclippedBoundsInRoot().bottom, "the plate sits under the cover")
        ok()
        assertEquals(1, joined)

        // Up is the cover, at whichever of its stops sits nearest.
        press(Key.DirectionUp)
        compose.onNode(isFocused() and inCover).assertExists()
    }

    @Test
    fun withNoSessionTheCoverTakesTheRemoteAsBefore() {
        home { null }
        compose.onNode(watchNow).assertIsFocused()
        compose.onNodeWithText("Watching together", substring = true).assertDoesNotExist()
    }

    @Test
    fun aSessionHeardAfterHomeAppearedLeavesTheRemoteWhereItIs() {
        var together: TogetherJoin? by mutableStateOf(null)
        home { together }
        compose.onNode(watchNow).assertIsFocused()

        compose.runOnUiThread { together = joining(shared) }
        compose.waitForIdle()

        compose.onNode(plate).assertExists()
        compose.onNode(watchNow).assertIsFocused()
    }

    @Test
    fun aSessionEndingUnderTheRemoteHandsItToThePageNotTheBar() {
        var together: TogetherJoin? by mutableStateOf(joining(shared))
        home { together }
        compose.onNode(plate).assertIsFocused()

        compose.runOnUiThread { together = null }
        compose.waitForIdle()

        compose.onNode(plate).assertDoesNotExist()
        // Nothing under it here, so the remote goes up to the cover.
        compose.onNode(isFocused() and inCover).assertExists()
    }

    private fun page(
        together: TogetherJoin?,
        content: @Composable () -> Unit,
    ) = show { CompositionLocalProvider(LocalTogetherJoin provides together, content = content) }

    @Test
    fun aFilmsPageJoinsInPlaceOfPlayAndTakesTheRemoteAsPlayDid() {
        page(joining(shared)) { TvTitlePage(set = shared, info = null, progress = null, onPlay = { error("Play is Join here") }) }
        compose.onNodeWithText("▶ Play").assertDoesNotExist()
        compose.onNodeWithText("▶ Join").assertIsFocused()
        ok()
        assertEquals(1, joined)
    }

    @Test
    fun anotherFilmsPageStillPlays() {
        page(joining(shared)) { TvTitlePage(set = coverFilm, info = null, progress = null, onPlay = {}) }
        compose.onNodeWithText("▶ Play").assertIsFocused()
        compose.onNodeWithText("▶ Join").assertDoesNotExist()
    }

    private fun collection(
        kind: CollectionKind,
        item: MediaSet,
    ) = Entry.Collection("k", kind, "A Collection", null, null, 1, 1, listOf(Division("Season 1", 1, listOf(item), emptyList())))

    @Test
    fun aShowsPageJoinsInPlaceOfItsResumePill() {
        val episode = set("ep", Kind.EPISODE, "Pilot", show = "A Show", addedAt = 0, episode = 1).copy(season = 1)
        val show = collection(CollectionKind.SHOW, episode)
        page(joining(episode)) {
            TvCollection(collection = show, info = null, watch = WatchSnapshot.Empty, onPlay = {}, resume = catalog.seriesResumeFor(show, WatchSnapshot.Empty))
        }
        compose.onNodeWithText("▶ Play", substring = true).assertDoesNotExist()
        compose.onNodeWithText("▶ Join").assertIsFocused()
        ok()
        assertEquals(1, joined)
    }

    @Test
    fun aCoursesResumeLineJoinsWhileTheSessionPlaysOneOfItsLessons() {
        val lesson = set("lesson", Kind.TUTORIAL, "Lesson 1", show = "A Course", addedAt = 0, episode = 1)
        val course = collection(CollectionKind.COURSE, lesson)
        page(joining(lesson)) {
            TvCollection(collection = course, info = null, watch = WatchSnapshot.Empty, onPlay = {}, resume = catalog.seriesResumeFor(course, WatchSnapshot.Empty))
        }
        compose.onNodeWithText("Play lesson", substring = true).assertDoesNotExist()
        compose.onNodeWithText("▶ Join").assertExists()
    }
}

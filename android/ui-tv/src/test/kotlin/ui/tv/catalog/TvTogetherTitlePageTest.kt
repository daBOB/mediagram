package ui.tv.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.pressKey
import catalog.CollectionKind
import catalog.Division
import catalog.Entry
import catalog.TogetherOffer
import catalog.seriesResumeFor
import kotlinx.coroutines.flow.MutableStateFlow
import model.Kind
import model.MediaSet
import model.WatchSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.common.catalog.LocalTogetherOffers
import ui.common.catalog.TogetherOffers
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Join on the television's title pages, as `together-join.js`'s `joinOr`
 * puts it on the web's: in place of Play or Resume on the page of the title
 * the session plays, on the same pill, so it takes the remote as Play did.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvTogetherTitlePageTest : TvScreenStateTest() {
    private var joined = 0
    private val shared = set("shared", Kind.MOVIE, "Shared Film", addedAt = 0)
    private val other = set("other", Kind.MOVIE, "Other Film", addedAt = 0)

    private fun ok() = compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionCenter) }

    private fun page(
        together: MediaSet,
        content: @Composable () -> Unit,
    ) {
        val offers = TogetherOffers(MutableStateFlow(TogetherOffer(41, together, "TV, Tablet"))) { joined++ }
        show { CompositionLocalProvider(LocalTogetherOffers provides offers, content = content) }
    }

    @Test
    fun aFilmsPageJoinsInPlaceOfPlayAndTakesTheRemoteAsPlayDid() {
        page(shared) { TvTitlePage(set = shared, info = null, progress = null, onPlay = { error("Play is Join here") }) }
        compose.onNodeWithText("▶ Play").assertDoesNotExist()
        compose.onNodeWithText("▶ Join").assertIsFocused()
        ok()
        assertEquals(1, joined)
    }

    @Test
    fun anotherFilmsPageStillPlays() {
        page(shared) { TvTitlePage(set = other, info = null, progress = null, onPlay = {}) }
        compose.onNodeWithText("▶ Play").assertIsFocused()
        compose.onNodeWithText("▶ Join").assertDoesNotExist()
    }

    private fun collection(
        kind: CollectionKind,
        item: MediaSet,
    ) = Entry.Collection("k", kind, "A Collection", null, null, 1, 1, divisions = listOf(Division("Season 1", 1, listOf(item), emptyList())))

    @Test
    fun aShowsPageJoinsInPlaceOfItsResumePill() {
        val episode = set("ep", Kind.EPISODE, "Pilot", show = "A Show", addedAt = 0, episode = 1).copy(season = 1)
        val show = collection(CollectionKind.SHOW, episode)
        page(episode) {
            TvCollection(show, info = null, watch = WatchSnapshot.Empty, onPlay = {}, resume = seriesResumeFor(show, WatchSnapshot.Empty))
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
        page(lesson) {
            TvCollection(course, info = null, watch = WatchSnapshot.Empty, onPlay = {}, resume = seriesResumeFor(course, WatchSnapshot.Empty))
        }
        compose.onNodeWithText("Play lesson", substring = true).assertDoesNotExist()
        compose.onNodeWithText("▶ Join").assertExists()
    }

    /** A long course scrolled past its head still listens, so the line under it never reads again on the way back. */
    @Test
    fun aCourseScrolledPastItsHeadKeepsListening() {
        val lessons = (1..60).map { set("lesson-$it", Kind.TUTORIAL, "Lesson $it", show = "A Course", addedAt = 0, episode = it) }
        val part = Division("Part 1", 1, lessons, emptyList())
        val course = Entry.Collection("k", CollectionKind.COURSE, "A Course", null, null, 60, 1, listOf(part))
        val flow = MutableStateFlow<TogetherOffer?>(TogetherOffer(41, lessons.first(), "TV"))
        show {
            CompositionLocalProvider(LocalTogetherOffers provides TogetherOffers(flow) { joined++ }) {
                val resume = seriesResumeFor(course, WatchSnapshot.Empty)
                TvCollection(course, info = null, watch = WatchSnapshot.Empty, onPlay = {}, resume = resume)
            }
        }
        compose.onNodeWithText("▶ Join").assertExists()

        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(55)
        compose.onNodeWithText("▶ Join").assertDoesNotExist()

        assertTrue(flow.subscriptionCount.value > 0, "the course page still listens with its head scrolled away")
    }
}

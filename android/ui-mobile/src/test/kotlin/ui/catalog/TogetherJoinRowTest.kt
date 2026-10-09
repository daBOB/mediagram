package ui.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import catalog.CollectionKind
import catalog.TogetherOffer
import model.MediaSet
import model.Progress
import model.WatchSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.common.catalog.LocalTogetherJoin
import ui.common.catalog.TogetherJoin
import kotlin.test.assertEquals

/**
 * Join where a viewer looks for something to watch, as `together-join.js`
 * offers it: one line on Home, and Join in place of Play or Resume on the
 * page of the title the session plays — a film's, a show's or a course's,
 * which has no Play of its own to stand in for.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp")
class TogetherJoinRowTest : BrowsePageTest() {
    private var joined = 0
    private var played = 0

    private fun joining(set: MediaSet) = TogetherJoin(TogetherOffer(41, set, "TV")) { joined++ }

    private fun offering(
        set: MediaSet?,
        content: @Composable () -> Unit,
    ) = show { CompositionLocalProvider(LocalTogetherJoin provides set?.let(::joining), content = content) }

    @Test
    fun homesLineNamesTheTitleAndWhoIsWatchingAndJoins() {
        show { TogetherJoinRow(line = "Watching together: Film · TV", onJoin = { joined++ }) }
        compose.onNodeWithText("Watching together: Film · TV").assertExists()
        compose.onNodeWithText("Join").performClick()
        assertEquals(1, joined)
    }

    @Test
    fun aFilmsPageJoinsInPlaceOfPlay() {
        val set = film("a")
        offering(set) { TitleDetailScreen(set = set, info = null, onPlay = { played++ }, onOpenGenre = {}) }
        compose.onNodeWithText("▶ Play").assertDoesNotExist()
        compose.onNodeWithText("▶ Join").performClick()
        assertEquals(1 to 0, joined to played)
    }

    @Test
    fun aFilmsPageJoinsInPlaceOfResume() {
        val set = film("a")
        val watch = WatchSnapshot.Empty.copy(progress = listOf(Progress(setId = "a", at = 1_200.0, duration = 6_000.0, updatedAt = 1)))
        offering(set) { TitleDetailScreen(set = set, info = null, onPlay = { played++ }, onOpenGenre = {}, watch = watch) }
        compose.onNodeWithText("Resume from", substring = true).assertDoesNotExist()
        compose.onNodeWithText("▶ Join").assertExists()
    }

    @Test
    fun anotherFilmsPageStillPlays() {
        val set = film("a")
        offering(film("b")) { TitleDetailScreen(set = set, info = null, onPlay = { played++ }, onOpenGenre = {}) }
        compose.onNodeWithText("▶ Join").assertDoesNotExist()
        compose.onNodeWithText("▶ Play").performClick()
        assertEquals(0 to 1, joined to played)
    }

    @Test
    fun aShowsPageJoinsInPlaceOfItsResumePill() {
        val show = collection("A Show", CollectionKind.SHOW)
        offering(show.divisions.single().items.single()) {
            CollectionScreen(collection = show, info = null, watch = WatchSnapshot.Empty, heldIds = emptySet(), onOpenTitle = {}, onOpenGenre = {}, onPlay = { played++ })
        }
        compose.onNodeWithText("▶ Play", substring = true).assertDoesNotExist()
        compose.onNodeWithText("▶ Join").performClick()
        assertEquals(1 to 0, joined to played)
    }

    @Test
    fun aCoursesPageOffersJoinWhileTheSessionPlaysOneOfItsLessons() {
        val course = collection("A Course", CollectionKind.COURSE)
        offering(course.divisions.single().items.single()) {
            CollectionScreen(collection = course, info = null, watch = WatchSnapshot.Empty, heldIds = emptySet(), onOpenTitle = {}, onOpenGenre = {})
        }
        compose.onNodeWithText("▶ Join").performClick()
        assertEquals(1, joined)
    }

    @Test
    fun aCoursesPageHasNoJoinForATitleOutsideIt() {
        val course = collection("A Course", CollectionKind.COURSE)
        offering(film("b")) {
            CollectionScreen(collection = course, info = null, watch = WatchSnapshot.Empty, heldIds = emptySet(), onOpenTitle = {}, onOpenGenre = {})
        }
        compose.onNode(hasText("A Course") and isHeading()).assertExists()
        compose.onNodeWithText("▶ Join").assertDoesNotExist()
    }
}

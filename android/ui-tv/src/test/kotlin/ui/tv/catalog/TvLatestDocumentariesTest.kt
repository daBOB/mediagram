package ui.tv.catalog

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import catalog.EditorialPicks
import catalog.Entry
import catalog.Latest
import catalog.MagazineHome
import model.Kind
import model.WatchSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The newest documentaries on Home and on Latest, played from their plates, and Latest's line of parts that jumps the remote to each. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvLatestDocumentariesTest : TvScreenStateTest() {
    private val films = (1..18).map { Entry.Film(set("film-$it", Kind.MOVIE, "Film $it", addedAt = 0)) }
    private val documentaries = listOf(set("doc-a", Kind.DOCUMENTARY, "Doc A", addedAt = 2), set("doc-b", Kind.DOCUMENTARY, "Doc B", addedAt = 1))
    private val latest =
        Latest(
            movies = films, series = emptyList(), courses = emptyList(),
            moviesTotal = films.size, seriesTotal = 0, coursesTotal = 0,
            documentaries = documentaries, documentariesTotal = documentaries.size,
        )

    @Test
    fun homesLatestDocumentariesRowPlaysTheTitle() {
        var played: String? = null
        show {
            TvHome(
                magazine = MagazineHome(EditorialPicks(emptyList(), emptyList(), null, emptyList()), emptyList(), emptyList(), 0),
                latest = latest.copy(movies = emptyList(), moviesTotal = 0),
                watch = WatchSnapshot.Empty,
                listState = rememberLazyListState(),
                onPlay = { played = it },
                onOpenTitle = {},
                onOpenCollection = {},
                onToggleWatchlist = { _, _ -> },
                onSeeAll = {},
                upExit = remember { FocusRequester() },
            )
        }
        compose.onNodeWithText("Latest documentaries", substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Doc A").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("doc-a", played)
    }

    @Test
    fun theDocumentariesPillHandsTheRemoteToTheFirstDocumentaryPastAWallOfFilms() {
        var played: String? = null
        show { TvLatestPage(latest, WatchSnapshot.Empty, emptySet(), onOpenTitle = {}, onOpenCollection = {}, onPlay = { played = it }) }
        // Each part's name twice: its pill under the page's heading, and its own heading over its plates.
        compose.onAllNodes(hasText("Movies")).assertCountEquals(2)
        // The pill, not the heading of the same name: the one that can be pressed.
        compose.onAllNodes(hasText("Documentaries") and hasClickAction())[0]
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithText("Doc A").assertIsFocused()
        compose.onNodeWithText("Doc A").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("doc-a", played)
    }
}

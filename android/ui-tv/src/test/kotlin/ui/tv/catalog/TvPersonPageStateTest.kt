package ui.tv.catalog

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import catalog.PersonPage
import model.Kind
import model.Person
import model.WatchSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * [TvPersonPage]: nobody by that id, or somebody nobody in this profile can
 * see, both say the fixed sentence and never a name — [catalog.personPageOf]'s
 * own rule. Still asking says its own loading mark instead, so the fixed
 * sentence never flashes up before an answer has arrived.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvPersonPageStateTest : TvScreenStateTest() {
    @Test
    fun nobodyByThatNumberSaysTheFixedSentenceWithNoName() {
        show { TvPersonPage(page = null, loading = false, portrait = null, watch = WatchSnapshot.Empty, heldIds = emptySet(), onOpenTitle = {}, onOpenCollection = {}) }

        compose.onNodeWithText("Nobody by that number is credited on anything in your library.").assertExists()
    }

    @Test
    fun stillLoadingNeverFlashesTheFixedSentence() {
        show { TvPersonPage(page = null, loading = true, portrait = null, watch = WatchSnapshot.Empty, heldIds = emptySet(), onOpenTitle = {}, onOpenCollection = {}) }

        compose.onNodeWithText("Nobody by that number is credited on anything in your library.").assertDoesNotExist()
    }

    @Test
    fun aRealPersonShowsTheirNameAndTheirTitlesOpen() {
        var opened: String? = null
        val person = Person(personId = 3L, name = "Ada Actor", portraitPath = null, titleKeys = listOf("film-0"))
        val film = set("film-0", Kind.MOVIE, "A Film", addedAt = 0)
        val page = PersonPage(person = person, films = listOf(film), shows = emptyList())
        show {
            TvPersonPage(
                page = page,
                loading = false,
                portrait = null,
                watch = WatchSnapshot.Empty,
                heldIds = emptySet(),
                onOpenTitle = { opened = it },
                onOpenCollection = {},
            )
        }

        compose.onNodeWithText("Ada Actor", substring = true).assertExists()
        compose.onNodeWithText("A Film").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals("film-0", opened)
    }

    @Test
    fun aHeldFilmSaysOffline() {
        val person = Person(personId = 3L, name = "Ada Actor", portraitPath = null, titleKeys = listOf("film-0"))
        val film = set("film-0", Kind.MOVIE, "A Film", addedAt = 0)
        val page = PersonPage(person = person, films = listOf(film), shows = emptyList())
        show {
            TvPersonPage(
                page = page,
                loading = false,
                portrait = null,
                watch = WatchSnapshot.Empty,
                heldIds = setOf("film-0"),
                onOpenTitle = {},
                onOpenCollection = {},
            )
        }

        compose.onNodeWithTag(TvOfflineBadgeTag, useUnmergedTree = true).assertExists()
    }
}

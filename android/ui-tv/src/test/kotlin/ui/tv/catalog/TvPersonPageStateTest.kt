package ui.tv.catalog

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import catalog.Entry
import catalog.PersonPage
import catalog.shelvesOf
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

    /**
     * The web's own head — the name over "2 in your library" in figures —
     * then Films and Series each under its own heading; the remote arrives
     * on the first film and Down crosses into Series. Tall enough that the
     * Series plate is laid out from the start: a real box composes it ahead
     * of the screen's edge in idle time, which Robolectric's clock never
     * gives a lazy grid.
     */
    @Test
    @Config(qualifiers = "w960dp-h1080dp")
    fun filmsAndSeriesAreTwoPartsUnderTheShelfHeadAndDownCrossesBetweenThem() {
        val person = Person(personId = 3L, name = "Ada Actor", portraitPath = null, titleKeys = listOf("film-0", "pilot"))
        val film = set("film-0", Kind.MOVIE, "A Film", addedAt = 0)
        val episode = set("pilot", Kind.EPISODE, "Pilot", show = "A Show", addedAt = 1, episode = 1)
        val shows = shelvesOf(listOf(episode)).first { it.title == "Series" }.entries.filterIsInstance<Entry.Collection>()
        val page = PersonPage(person = person, films = listOf(film), shows = shows)
        show {
            TvPersonPage(page = page, loading = false, portrait = null, watch = WatchSnapshot.Empty, heldIds = emptySet(), onOpenTitle = {}, onOpenCollection = {})
        }

        compose.onNodeWithText("Ada Actor").assertExists()
        compose.onNodeWithText("2 IN YOUR LIBRARY").assertExists()
        compose.onNodeWithText("Films").assertExists()
        compose.onNodeWithText("Series").assertExists()
        compose.onNodeWithText("A Film").assertIsFocused()

        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("A Show").assertIsFocused()
    }

    /** A person in films alone still gets the web's "Films" label — it heads its parts unconditionally, unlike a genre's wall. */
    @Test
    fun aLonePartIsStillHeaded() {
        val person = Person(personId = 3L, name = "Ada Actor", portraitPath = null, titleKeys = listOf("film-0"))
        val page = PersonPage(person = person, films = listOf(set("film-0", Kind.MOVIE, "A Film", addedAt = 0)), shows = emptyList())
        show {
            TvPersonPage(page = page, loading = false, portrait = null, watch = WatchSnapshot.Empty, heldIds = emptySet(), onOpenTitle = {}, onOpenCollection = {})
        }

        compose.onNodeWithText("Films").assertExists()
        compose.onNodeWithText("Series").assertDoesNotExist()
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

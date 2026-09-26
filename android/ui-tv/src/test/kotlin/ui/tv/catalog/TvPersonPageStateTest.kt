package ui.tv.catalog

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import catalog.PersonPage
import model.Kind
import model.Person
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * [TvPersonPage]: nobody by that id, or somebody nobody in this profile can
 * see, both say the fixed sentence and never a name — [catalog.personPageOf]'s
 * own rule, ported to the television for the first time by this phase.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvPersonPageStateTest : TvScreenStateTest() {
    @Test
    fun nobodyByThatNumberSaysTheFixedSentenceWithNoName() {
        show { TvPersonPage(page = null, portrait = null, onOpenTitle = {}, onOpenCollection = {}) }

        compose.onNodeWithText("Nobody by that number is credited on anything in your library.").assertExists()
    }

    @Test
    fun aRealPersonShowsTheirNameAndTheirTitlesOpen() {
        var opened: String? = null
        val person = Person(personId = 3L, name = "Ada Actor", portraitPath = null, titleKeys = listOf("film-0"))
        val film = set("film-0", Kind.MOVIE, "A Film", addedAt = 0)
        val page = PersonPage(person = person, films = listOf(film), shows = emptyList())
        show { TvPersonPage(page = page, portrait = null, onOpenTitle = { opened = it }, onOpenCollection = {}) }

        compose.onNodeWithText("Ada Actor", substring = true).assertExists()
        compose.onNodeWithText("A Film").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals("film-0", opened)
    }
}

package ui.tv.catalog

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.test.platform.app.InstrumentationRegistry
import catalog.CollectionKind
import catalog.Division
import catalog.Entry
import model.Credit
import model.Kind
import model.TitleCredits
import model.WatchSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * The Cast tab's crew line on television — "Directed by" a film's crew,
 * "Created by" a show's — with each name a stop that opens that person's
 * page, as `cast.js` links them, and the remote finding its way between
 * the names, the plates under them and the tab row above.
 *
 * The walks leave touch mode, where a remote always is: a plain clickable
 * text row takes no focus in it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvCastCrewLinksStateTest : TvScreenStateTest() {
    private val film = set("f", Kind.MOVIE, "A Film", addedAt = 0)
    private val ada = Credit(personId = 7L, name = "Ada Actor", role = "Herself", portraitPath = null)
    private val dee = Credit(personId = 3L, name = "Dee Director", role = "Director", portraitPath = null)
    private val eve = Credit(personId = 4L, name = "Eve Director", role = "Director", portraitPath = null)
    private val credits = TitleCredits(cast = listOf(ada), crew = listOf(dee, eve))

    @Test
    fun eachDirectorsNameOpensTheirPage() {
        val opened = mutableListOf<Long>()
        show { TvTitlePage(set = film, info = null, progress = null, onPlay = {}, credits = credits, onOpenPerson = { opened += it }) }

        compose.onNodeWithText("Cast").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Directed by").assertExists()
        compose.onNodeWithText("Dee Director").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Eve Director").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf(3L, 4L), opened)
    }

    /** A show's crew are its creators, and say so — linked the same way. */
    @Test
    fun aShowsCreatorsAreLinkedUnderCreatedBy() {
        var opened: Long? = null
        val creator = Credit(personId = 9L, name = "Cy Creator", role = "Creator", portraitPath = null)
        val series = Entry.Collection("SHOW/A Show", CollectionKind.SHOW, "A Show", null, null, 1, 1, listOf(Division("Season 1", 1, listOf(set("e1", Kind.EPISODE, "Pilot", show = "A Show", addedAt = 0)), emptyList())))
        show {
            TvCollection(series, info = null, watch = WatchSnapshot.Empty, onPlay = {}, credits = TitleCredits(listOf(ada), listOf(creator)), onOpenPerson = { opened = it })
        }

        compose.onNodeWithText("Cast").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Created by").assertExists()
        compose.onNodeWithText("Cy Creator").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(9L, opened)
    }

    /** Up from a plate reaches the names, Right walks them, Up again the Cast tab — not the tab nearest — and Down comes back through them. */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun theRemoteWalksFromThePlatesThroughTheNamesToTheCastTab() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        show { TvTitlePage(set = film, info = null, progress = null, onPlay = {}, credits = credits, restoreKey = "7") }
        compose.onNodeWithText("Ada Actor").assertIsFocused()

        press(Key.DirectionUp)
        compose.onNodeWithText("Dee Director").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithText("Eve Director").assertIsFocused()
        press(Key.DirectionUp)
        compose.onNodeWithText("Cast").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithText("Dee Director").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithText("Ada Actor").assertIsFocused()
    }

    /** Back from a director's page — someone the cast does not name — opens on Cast with their name holding the remote. */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun comingBackFromADirectorLandsOnTheirNameOnTheCastTab() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        show { TvTitlePage(set = film, info = null, progress = null, onPlay = {}, credits = credits, restoreKey = "4") }

        compose.onNodeWithText("Ada Actor").assertExists()
        compose.onNodeWithText("Eve Director").assertIsFocused()
    }

    /** A director who also acts comes back to their plate, the larger stop. */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aDirectorInTheCastComesBackToTheirPlate() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        val both = TitleCredits(cast = listOf(ada, dee.copy(role = "Himself")), crew = listOf(dee))
        show { TvTitlePage(set = film, info = null, progress = null, onPlay = {}, credits = both, restoreKey = "3") }

        compose.onNodeWithText("Himself", substring = true).assertIsFocused()
    }

    private fun press(key: Key) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }
}

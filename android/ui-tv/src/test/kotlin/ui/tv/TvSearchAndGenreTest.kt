package ui.tv

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import model.Kind
import model.MediaSet
import model.PersonHit
import model.Profile
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit
import ui.tv.catalog.TvSearchFieldTag
import ui.tv.catalog.films
import ui.tv.catalog.set
import ui.tv.player.TvPlayerScreenTag

/**
 * Search and genre pages walked the way a remote walks them, over the real
 * `CatalogViewModel` and `SearchViewModel` [TvAppFixture] builds: something
 * has the remote the moment each appears, and Back lands on whatever opened
 * it — the bar's own Search button, the row that played, the genre link
 * pressed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvSearchAndGenreTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var fixture: TvAppFixture
    private lateinit var controller: ActivityController<TvAppTestActivity>

    @Before
    fun open() {
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
        compose.runOnUiThread {
            val sets =
                films(2).map {
                    val withGenres = if (it.setId == "film-1") it.copy(genres = listOf("Drama", "Comedy")) else it
                    // A stable poster key per film, so a grouped-search test can
                    // name one as a person's own credit (`PersonHit.titleKeys`) —
                    // `visiblePeople`'s own visibility check needs one to match.
                    withGenres.copy(posterKey = "poster-${it.setId}")
                } + show()
            fixture = TvAppFixture(TvSetupStage.READY, listOf(Profile(id = "ada", name = "Ada")), "ada", sets)
            TvAppTestActivity.fixture = fixture
            controller = Robolectric.buildActivity(TvAppTestActivity::class.java).setup().visible()
        }
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(hasText("Film 1")).fetchSemanticsNodes().isNotEmpty() }
    }

    @After
    fun close() {
        try {
            compose.runOnUiThread {
                if (::controller.isInitialized) controller.close()
                if (::fixture.isInitialized) fixture.close()
            }
        } finally {
            unmockkStatic(::HiltViewModelFactory)
        }
    }

    @Test
    fun searchOpensOnItsFieldAndBackReturnsToTheMastheadEntry() {
        press(compose.onNodeWithContentDescription("Search"))
        field().assertIsFocused()

        back()

        compose.onNodeWithContentDescription("Search").assertIsFocused()
    }

    @Test
    fun theSearchKeyHandsTheRemoteToTheFirstPosterAndUpGoesBackToTheField() {
        press(compose.onNodeWithContentDescription("Search"))
        type("film")
        compose.onNodeWithText("2 results").assertExists()

        field().performImeAction()
        compose.waitForIdle()
        row("Film 0").assertIsFocused()

        key(KeyEvent.KEYCODE_DPAD_UP)
        field().assertIsFocused()
    }

    /**
     * Down leaves the field for the results by itself, the keyboard put
     * away or never shown: the text field only moves focus for a D-pad on
     * a physical device, and a remote's keys may arrive as a virtual one.
     * It lands on the first poster, not on whichever one sits under the
     * middle of the full-width field; Right walks the line, Up from any
     * poster on it goes back to the field.
     */
    @Test
    fun downFromTheFieldReachesTheFirstPosterAndRightWalksTheLine() {
        press(compose.onNodeWithContentDescription("Search"))
        type("film")
        compose.onNodeWithText("2 results").assertExists()
        field().assertIsFocused()

        key(KeyEvent.KEYCODE_DPAD_DOWN)
        row("Film 0").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        row("Film 1").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_UP)
        field().assertIsFocused()
    }

    /**
     * Films are posters, as on the web and the phone, and a poster opens
     * the film's own page rather than playing it; the second poster, so
     * landing back on it is the one pressed rather than simply the first.
     */
    @Test
    fun aFilmsPosterOpensItsPageAndBackLandsOnThatPoster() {
        press(compose.onNodeWithContentDescription("Search"))
        type("film")

        press(row("Film 1"))
        compose.onNodeWithText("▶ Play").assertExists()
        compose.onNodeWithTag(TvPlayerScreenTag).assertDoesNotExist()

        back()
        awaitNode(hasText("Film 1") and hasClickAction())
        row("Film 1").assertIsFocused()
        compose.onNodeWithText("film").assertExists()
    }

    /** A matched episode is a row under its show's poster, and still plays at once; Back from the player lands on that row. */
    @Test
    fun anEpisodeRowPlaysAndBackFromThePlayerLandsOnThatRow() {
        press(compose.onNodeWithContentDescription("Search"))
        type("pilot")
        compose.onNodeWithText("Series").assertExists()
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        plate("A Show").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        row("Pilot").assertIsFocused()

        press(row("Pilot"))
        compose.onNodeWithTag(TvPlayerScreenTag).assertExists()

        back()
        back()
        awaitNode(hasText("Pilot") and hasClickAction())
        row("Pilot").assertIsFocused()
    }

    @Test
    fun aQueryNothingMentionsSaysSoInThePhonesWords() {
        press(compose.onNodeWithContentDescription("Search"))
        type("zebra")

        compose.onNodeWithText("No title, folder or summary in the library mentions that.").assertExists()
        field().assertIsFocused()
    }

    /**
     * A row asked for once is not asked for again: going back up to the
     * field and typing through an answer with no rows, then back to one
     * with rows, leaves the remote in the field.
     */
    @Test
    fun typingThroughNoHitsAfterTheSearchKeyKeepsTheRemoteInTheField() {
        press(compose.onNodeWithContentDescription("Search"))
        type("film")
        field().performImeAction()
        compose.waitForIdle()
        row("Film 0").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_UP)
        field().assertIsFocused()

        type("zz")
        compose.onNodeWithText("No title, folder or summary in the library mentions that.").assertExists()
        field().performTextReplacement("film")
        settle()

        compose.onNodeWithText("2 results").assertExists()
        field().assertIsFocused()
    }

    @Test
    fun aNewQueryAfterComingBackFromATitleKeepsTheRemoteInTheField() {
        press(compose.onNodeWithContentDescription("Search"))
        type("film")
        press(row("Film 1"))
        back()
        awaitNode(hasText("Film 1") and hasClickAction())
        row("Film 1").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_UP)
        field().assertIsFocused()

        field().performTextClearance()
        settle()
        type("film")

        compose.onNodeWithText("2 results").assertExists()
        field().assertIsFocused()
    }

    /** Down from Search enters Home by its own first stop, and the way back to Search is spent once used. */
    @Test
    fun downFromTheMastheadsSearchLandsOnHomesFirstStop() {
        press(compose.onNodeWithContentDescription("Search"))
        back()
        compose.onNodeWithContentDescription("Search").assertIsFocused()

        key(KeyEvent.KEYCODE_DPAD_DOWN)

        plate("Film 1").assertIsFocused()
    }

    /** Film 1 carries Drama, as does the show: both kinds, so the wall labels each. */
    @Test
    fun aTitlesGenreOpensItsWallAndBackLandsOnTheLinkThenPlay() {
        press(plate("Film 1"))
        press(compose.onNodeWithText("Drama"))

        compose.onNodeWithText("Movies").assertExists()
        compose.onNodeWithText("Series").assertExists()
        plate("Film 1").assertIsFocused()

        press(plate("A Show"))
        back()
        plate("A Show").assertIsFocused()

        back()
        compose.onNodeWithText("Drama").assertIsFocused()

        press(compose.onNodeWithText("▶ Play"))
        back()
        back()
        compose.onNodeWithText("▶ Play").assertIsFocused()
    }

    @Test
    fun aShowsGenreLinkIsWhereBackFromItsWallLands() {
        press(plate("A Show"))
        // A show's genres are links in About, as `series-page.js` puts them.
        press(compose.onNodeWithText("About"))
        press(compose.onNodeWithText("Drama"))
        plate("Film 1").assertIsFocused()

        back()

        compose.onNodeWithText("Drama").assertIsFocused()
        back()
        plate("A Show").assertIsFocused()
    }

    /**
     * People (only those the profile can see — `visiblePeople`'s own rule)
     * and filter chips: pressing "People" narrows to just that section, and
     * pressing a person's row opens their own page.
     */
    @Test
    fun peopleGroupWithFilterChipsNarrowsToJustThatSection() {
        val hit = PersonHit(personId = 9L, name = "Ada Actor", portraitPath = null, titleKeys = listOf("poster-film-0"))
        coEvery { fixture.repository.searchPeople(any()) } returns listOf(hit)

        press(compose.onNodeWithContentDescription("Search"))
        type("film")

        compose.onNodeWithText("●  All · 3").assertExists()
        compose.onNodeWithText("○  Movies · 2").assertExists()
        // Down from the field reaches the chips, Down from a chip enters the
        // results at their first poster, and Down from the posters reaches
        // the person's row below them.
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        row("Film 0").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithText("Ada Actor").assertIsFocused()
        press(compose.onNodeWithText("○  People · 1"))

        compose.onNodeWithText("Film 0").assertDoesNotExist()
        compose.onNodeWithText("Ada Actor").assertExists()

        press(compose.onNodeWithText("Ada Actor"))
        compose.onNodeWithText("Nobody by that number is credited on anything in your library.").assertExists()
    }

    /**
     * Back from a person opened in search lands on that person's own row,
     * below the films — the row is recorded under the results' own key for
     * it, not the bare id, which no row carries and so fell to the first.
     */
    @Test
    fun backFromAPersonLandsOnTheirRowNotTheFirstResult() {
        val hit = PersonHit(personId = 9L, name = "Ada Actor", portraitPath = null, titleKeys = listOf("poster-film-0"))
        coEvery { fixture.repository.searchPeople(any()) } returns listOf(hit)

        press(compose.onNodeWithContentDescription("Search"))
        type("film")
        // Below the posters, past the fold: walked to, as a remote would.
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        press(compose.onNodeWithText("Ada Actor"))
        back()
        settle()

        compose.onNodeWithText("Ada Actor").assertIsFocused()
    }

    /** A person nobody in the library can see — no title key of theirs matches anything — is never offered at all. */
    @Test
    fun aPersonWithNoVisibleTitleNeverAppears() {
        val hit = PersonHit(personId = 9L, name = "Nobody Here", portraitPath = null, titleKeys = listOf("no-such-poster"))
        coEvery { fixture.repository.searchPeople(any()) } returns listOf(hit)

        press(compose.onNodeWithContentDescription("Search"))
        type("film")

        compose.onNodeWithText("Nobody Here").assertDoesNotExist()
        // Two kinds only (Movies), so no chip row at all — nothing to narrow to.
        compose.onAllNodesWithText("●  All", substring = true).assertCountEquals(0)
    }

    private fun field() = compose.onNodeWithTag(TvSearchFieldTag)

    private fun row(title: String) = compose.onNode(hasText(title) and hasClickAction())

    private fun plate(name: String) = compose.onNode(hasText(name) and hasClickAction())

    /** Typed, then waited out past the search's own pause for the answer. */
    private fun type(text: String) {
        field().performTextInput(text)
        settle()
    }

    /** The search's pause runs on the main looper's own clock, which only moves when told to. */
    private fun settle() {
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS)
        compose.waitForIdle()
    }

    private fun awaitNode(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        settle()
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    private fun press(node: SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun key(code: Int) {
        compose.runOnUiThread { controller.get().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)) }
        compose.runOnUiThread { controller.get().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code)) }
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { controller.get().onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    /** One show of two seasons, tagged Drama — a show page whose About names the genre as a link. */
    private fun show(): List<MediaSet> =
        listOf(
            set("pilot", Kind.EPISODE, "Pilot", show = "A Show", addedAt = 10, episode = 1).copy(season = 1, genres = listOf("Drama")),
            set("return", Kind.EPISODE, "Return", show = "A Show", addedAt = 11, episode = 1).copy(season = 2, genres = listOf("Drama")),
        )
}

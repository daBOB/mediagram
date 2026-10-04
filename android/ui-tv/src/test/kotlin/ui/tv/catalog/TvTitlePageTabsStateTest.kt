package ui.tv.catalog

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import model.Credit
import model.Kind
import model.TitleCredits
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uniffi.mediagram_core.TitleInfo
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [TvTitlePage]'s tabs and pills — Overview/Cast/Similar/Details as the
 * web's film page tabs them, My List and the ⋯ beside Play — and what each
 * panel says.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvTitlePageTabsStateTest : TvScreenStateTest() {
    private val film = set("f", Kind.MOVIE, "A Film", addedAt = 0, year = 2004, durationSecs = 6780).copy(fsk = "12", genres = listOf("Drama"))

    /** Similar is always a tab, as on the web, and says so when nothing is like this film; Cast only once credits name somebody. */
    @Test
    fun withNoCastSimilarIsStillOfferedAndSaysNothingIsLikeIt() {
        show { TvTitlePage(set = film, info = null, progress = null, onPlay = {}) }

        listOf("Overview", "Similar", "Details").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("Cast").assertDoesNotExist()
        compose.onNodeWithText("Similar").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText(NothingSimilar).assertExists()
    }

    @Test
    fun theOverviewIsAFactSheetWhoseGenresAndFranchiseOpen() {
        var genre: String? = null
        var franchise: Long? = null
        val saga = set("s", Kind.MOVIE, "Sequel", addedAt = 1).copy(collectionId = 9, collectionName = "The Saga")
        show {
            TvTitlePage(
                set = film.copy(collectionId = 9, collectionName = "The Saga"),
                info = TitleInfo(overview = null, tagline = null, genres = null, rating = 7.5, network = null, status = null),
                progress = null,
                onPlay = {},
                onOpenGenre = { genre = it },
                allFilms = listOf(film.copy(collectionId = 9, collectionName = "The Saga"), saga),
                onOpenFranchise = { franchise = it },
            )
        }

        listOf("RELEASED" to "2004", "RUNTIME" to "1h 53m", "RATED" to "FSK 12", "SCORE" to "★ 7.5").forEach { (label, value) ->
            compose.onNodeWithText(label).assertExists()
            compose.onNodeWithText(value).assertExists()
        }
        compose.onNodeWithText("Drama").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("Drama", genre)
        compose.onNodeWithText("The Saga").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(9L, franchise)
    }

    /** `film-page.js#details`: the file, row by row, the languages by name. */
    @Test
    fun detailsIsTheFileAsTheWebPrintsIt() {
        val file = film.copy(container = "mkv", vcodec = "hevc", acodec = "eac3", quality = "1080p", alang = listOf("en", "de"), slang = listOf("de"))
        show { TvTitlePage(set = file, info = null, progress = null, onPlay = {}) }

        compose.onNodeWithText("Details").performSemanticsAction(SemanticsActions.OnClick)

        listOf("QUALITY" to "1080p", "VIDEO" to "hevc", "AUDIO" to "eac3", "AUDIO LANGUAGES" to "English, German", "SUBTITLES" to "German", "CONTAINER" to "mkv")
            .forEach { (label, value) ->
                compose.onNodeWithText(label).assertExists()
                compose.onNodeWithText(value).assertExists()
            }
        compose.onNodeWithText("PARTS").assertDoesNotExist()
    }

    @Test
    fun castGetsItsOwnTabAndPressingAPersonOpensThem() {
        var opened: Long? = null
        val credits = TitleCredits(cast = listOf(Credit(personId = 7L, name = "Ada Actor", role = "Herself", portraitPath = null)), crew = emptyList())
        show { TvTitlePage(set = film, info = null, progress = null, onPlay = {}, credits = credits, onOpenPerson = { opened = it }) }

        compose.onNodeWithText("Cast").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Ada Actor").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(7L, opened)
    }

    @Test
    fun similarGetsItsOwnTabAndPressingATitleOpensIt() {
        var opened: String? = null
        val other = set("g", Kind.MOVIE, "Another Film", addedAt = 1)
        show { TvTitlePage(set = film, info = null, progress = null, onPlay = {}, similar = listOf(other), onOpenTitle = { opened = it }) }

        compose.onNodeWithText("Similar").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Another Film").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals("g", opened)
    }

    /** `list-toggle.js`'s pill, worded as the players word it. */
    @Test
    fun myListSaysWhetherTheFilmIsOnItAndToggles() {
        var toggled = false
        show { TvTitlePage(set = film, info = null, progress = null, onPlay = {}, onToggleWatchlist = { toggled = true }) }
        compose.onNodeWithText("+ My List").performSemanticsAction(SemanticsActions.OnClick)
        assertTrue(toggled)
        close()

        show { TvTitlePage(set = film, info = null, progress = null, onPlay = {}, watchlisted = true) }
        compose.onNodeWithText("✓ My List").assertExists()
    }

    /** The ⋯ opens its choice beside it and takes the remote there; pressing it puts the remote back on ⋯. */
    @Test
    fun theMoreMenuOffersTheEditorsChoiceToggle() {
        var toggled = false
        show { TvTitlePage(set = film, info = null, progress = null, onPlay = {}, editorsChoice = null, onToggleEditorsChoice = { toggled = true }) }
        compose.onNodeWithText("Make editor's choice").assertDoesNotExist()

        compose.onNodeWithContentDescription("More").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Make editor's choice").assertIsFocused()
        compose.onNodeWithText("Make editor's choice").performSemanticsAction(SemanticsActions.OnClick)

        assertTrue(toggled)
        compose.onNodeWithText("Make editor's choice").assertDoesNotExist()
        compose.onNodeWithContentDescription("More").assertIsFocused()
    }

    /** Back closes an open ⋯ onto its button; with it closed the page takes no Back of its own, so Back still leaves the page. */
    @Test
    fun backClosesTheMoreMenuAndOtherwiseIsTheFramesOwn() {
        show { TvTitlePage(set = film, info = null, progress = null, onPlay = {}, editorsChoice = film.setId, onToggleEditorsChoice = {}) }
        assertFalse(pageTakesBack())

        compose.onNodeWithContentDescription("More").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Remove as editor's choice").assertIsFocused()
        back()

        compose.onNodeWithText("Remove as editor's choice").assertDoesNotExist()
        compose.onNodeWithContentDescription("More").assertIsFocused()
        assertFalse(pageTakesBack())
    }

    /** A kids profile's `null` leaves the ⋯ out entirely — the same gate the web's `moreMenu` applies. */
    @Test
    fun aKidsProfileHasNoMoreMenu() {
        show { TvTitlePage(set = film, info = null, progress = null, onPlay = {}, onToggleEditorsChoice = null) }

        compose.onNodeWithContentDescription("More").assertDoesNotExist()
    }

    /** Back from a genre's page lands on its link in the Overview, not on Play. */
    @Test
    fun comingBackFromAGenreLandsOnItsLink() {
        show { TvTitlePage(set = film, info = null, progress = null, onPlay = {}, restoreKey = "Drama") }

        compose.onNodeWithText("Drama").assertIsFocused()
        compose.onNodeWithText("▶ Play").assertIsNotFocused()
    }

    /**
     * A page rebuilt fresh on the way back from a person's page (this page
     * is torn down while that one is shown, not kept alive underneath it)
     * still lands on Cast, and on that person, because [restoreKey] — not a
     * `rememberSaveable` this rebuild has nothing saved for — is what says so.
     */
    @Test
    fun comingBackFromAPersonLandsOnCastTabWithThatPersonFocused() {
        val credits =
            TitleCredits(
                cast =
                    listOf(
                        Credit(personId = 7L, name = "Ada Actor", role = "Herself", portraitPath = null),
                        Credit(personId = 8L, name = "Bo Actor", role = "Himself", portraitPath = null),
                    ),
                crew = emptyList(),
            )
        show { TvTitlePage(set = film, info = null, progress = null, onPlay = {}, credits = credits, restoreKey = "8") }

        compose.onNodeWithText("Bo Actor").assertIsFocused()
    }

    /**
     * What the real library does on the way back: credits are looked up
     * again and arrive after the page is drawn, empty until then. The
     * restore must still land on the Cast tab and that person once they do.
     */
    @Test
    fun comingBackLandsOnCastEvenWhenCreditsArriveAfterThePage() {
        val arrived =
            TitleCredits(
                cast = listOf(Credit(personId = 8L, name = "Bo Actor", role = "Himself", portraitPath = null)),
                crew = emptyList(),
            )
        val credits = mutableStateOf(TitleCredits.Empty)
        show { TvTitlePage(set = film, info = null, progress = null, onPlay = {}, credits = credits.value, restoreKey = "8") }

        compose.runOnUiThread { credits.value = arrived }
        compose.waitForIdle()

        compose.onNodeWithText("Bo Actor").assertIsFocused()
    }

    /** The same restore rule, for a similar film opened from the Similar tab. */
    @Test
    fun comingBackFromASimilarFilmLandsOnSimilarTabWithThatFilmFocused() {
        val other = set("g", Kind.MOVIE, "Another Film", addedAt = 1)
        show { TvTitlePage(set = film, info = null, progress = null, onPlay = {}, similar = listOf(other), restoreKey = "g") }

        compose.onNodeWithText("Another Film").assertIsFocused()
    }
}

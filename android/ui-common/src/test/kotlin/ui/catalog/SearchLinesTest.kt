package ui.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import model.Kind
import model.MediaSet

/** Mirrors `locationOf` in the web's `search-view.js`, one case per kind. */
class SearchLinesTest {

    /** The one hit search cannot open — the player would be handed a PDF. */
    @Test
    fun aDocumentIsShownButNotPlayable() {
        assertFalse(isPlayable(fixture(Kind.DOCUMENT)))
        assertTrue(isPlayable(fixture(Kind.MOVIE)))
        assertTrue(isPlayable(fixture(Kind.EPISODE)))
        assertTrue(isPlayable(fixture(Kind.TUTORIAL)))
    }

    @Test
    fun aFilmSaysTheYearItIsFrom() {
        assertEquals("2004", locationOf(fixture(Kind.MOVIE, year = 2004)))
    }

    @Test
    fun aFilmWithNoYearSaysNothing() {
        assertNull(locationOf(fixture(Kind.MOVIE, year = null)))
        assertNull(locationOf(fixture(Kind.MOVIE, year = 0)))
    }

    @Test
    fun anEpisodeSaysItsShowAndItsNumber() {
        assertEquals(
            "30 Rock · S1E4",
            locationOf(fixture(Kind.EPISODE, show = "30 Rock", season = 1, episodeFirst = 4)),
        )
    }

    @Test
    fun anUnnumberedEpisodeSaysOnlyItsShow() {
        assertEquals("30 Rock", locationOf(fixture(Kind.EPISODE, show = "30 Rock")))
    }

    @Test
    fun aLessonSaysItsCourseAndTheFolderItSitsIn() {
        assertEquals(
            "German A1 · Grammar",
            locationOf(fixture(Kind.TUTORIAL, show = "German A1", path = "Grammar")),
        )
    }

    /** The folder path reads better than the generated chapter label, the same rule the shelves show. */
    @Test
    fun aLessonWithNoFolderPathFallsBackToItsChapter() {
        assertEquals(
            "German A1 · Verbs",
            locationOf(fixture(Kind.TUTORIAL, show = "German A1", path = null, chapter = "Verbs")),
        )
    }

    @Test
    fun theCountReadsSingularAtOne() {
        assertEquals("1 result", countOf(1, "result"))
        assertEquals("2 results", countOf(2, "result"))
        assertEquals("12 titles", countOf(12, "title"))
    }

    /** A restore settling on an answer before the catalog loads reads as "not yet", not "nothing found". */
    @Test
    fun theCatalogNotYetReadyIsLoadingEvenWithAnAnswerInHand() {
        assertEquals(SearchResultsView.LOADING, searchResultsView(catalogReady = false, rowsEmpty = true))
        assertEquals(SearchResultsView.LOADING, searchResultsView(catalogReady = false, rowsEmpty = false))
    }

    @Test
    fun aReadyCatalogWithNoJoinedRowsIsEmpty() {
        assertEquals(SearchResultsView.EMPTY, searchResultsView(catalogReady = true, rowsEmpty = true))
    }

    @Test
    fun aReadyCatalogWithRowsShowsThem() {
        assertEquals(SearchResultsView.ROWS, searchResultsView(catalogReady = true, rowsEmpty = false))
    }

    private fun fixture(
        kind: Kind,
        show: String? = null,
        chapter: String? = null,
        path: String? = null,
        season: Int? = null,
        episodeFirst: Int? = null,
        year: Int? = null,
    ): MediaSet = MediaSet(
        setId = "set-1",
        kind = kind,
        title = "A Title",
        show = show,
        chapter = chapter,
        path = path,
        season = season,
        episodeFirst = episodeFirst,
        episodeLast = episodeFirst,
        year = year,
        durationSecs = null,
        posterPath = null,
        totalBytes = 0,
    )
}

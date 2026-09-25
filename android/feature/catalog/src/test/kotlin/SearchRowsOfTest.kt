package catalog

import model.Kind
import model.MediaSet
import model.WatchSnapshot
import uniffi.mediagram_core.SearchHit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [searchRowsOf] joins a hit onto the catalog already on screen rather than
 * asking the core to rebuild it — the fix for a search that recomputed
 * every [MediaSet] and re-checked every poster's file on the main thread,
 * once per keystroke.
 */
class SearchRowsOfTest {

    private val set = MediaSet(
        setId = "movie-0",
        kind = Kind.MOVIE,
        title = "A Film",
        show = null,
        chapter = null,
        path = null,
        season = null,
        episodeFirst = null,
        episodeLast = null,
        year = 2004,
        durationSecs = null,
        posterPath = null,
        totalBytes = 0,
    )
    private val ready = CatalogUiState.Ready(shelves = listOf(Shelf("Movies", listOf(Entry.Film(set)))), watch = WatchSnapshot.Empty)

    @Test
    fun aHitIsJoinedToTheSetItNamed() {
        val hit = SearchHit(setId = "movie-0", matched = "summary", excerpt = "…mentioned here…")
        assertEquals(listOf(SearchRow(set, "summary", "…mentioned here…")), searchRowsOf(listOf(hit), ready))
    }

    @Test
    fun aHitNamingAGoneSetIsDropped() {
        val hit = SearchHit(setId = "gone", matched = "title", excerpt = null)
        assertTrue(searchRowsOf(listOf(hit), ready).isEmpty())
    }

    @Test
    fun aCatalogStillLoadingJoinsNothing() {
        assertTrue(searchRowsOf(listOf(SearchHit("movie-0", "title", null)), CatalogUiState.Loading).isEmpty())
    }
}

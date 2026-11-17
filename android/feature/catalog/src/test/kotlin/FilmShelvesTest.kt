package catalog

import model.MediaSet
import model.Progress
import model.WatchSnapshot
import model.Watched
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Where the film rules reach the shelves: one card per film, every copy still findable by its own id. */
class FilmShelvesTest {
    private val hd = movie("HD", posterKey = "tmdb-movie-1", quality = "1080p")
    private val uhd = movie("UHD", posterKey = "tmdb-movie-1", quality = "2160p")
    private val shelves = shelvesOf(listOf(hd, uhd, movie("A1", "tmdb-movie-2").copy(anime = true), movie("A2", "tmdb-movie-2").copy(anime = true, variant = "Remastered")))

    private fun watch(
        progress: List<Progress> = emptyList(),
        watched: List<Watched> = emptyList(),
        watchlist: List<String> = emptyList(),
    ) = WatchSnapshot(progress, watched, watchlist, emptyList(), emptyList())

    @Test
    fun moviesAndAnimeFilmsShowOneCardPerFilm() {
        fun cards(department: Department) =
            shelves.single { it.department == department }.entries.map { (it as Entry.Film).set.setId to versionsOf(it.set).size }
        assertEquals(listOf("UHD" to 2), cards(Department.MOVIES))
        assertEquals(listOf("A1" to 2), cards(Department.ANIME))
    }

    @Test
    fun everyCopyStaysFindableByItsOwnIdAndOpensAsItsFilm() {
        val state = CatalogUiState.Ready(shelves)
        assertEquals(setOf("HD", "UHD", "A1", "A2"), allSetsById(shelves).keys)
        assertEquals("HD", state.mediaSet("HD")?.setId)
        assertEquals("UHD", state.filmOf("HD")?.setId)
        assertNull(state.filmOf("nothing"))
    }

    @Test
    fun continueListsAFilmOnceAndMyListShowsTheFilm() {
        val rows = listOf(Progress("HD", 1_800.0, 3_600.0, 2), Progress("UHD", 900.0, 3_600.0, 1))
        assertEquals(listOf("HD"), continueWall(shelves, watch(progress = rows)).map(MediaSet::setId))
        assertEquals(listOf("UHD"), watchlistWall(shelves, watch(watchlist = listOf("HD", "UHD"))).map(MediaSet::setId))
    }

    @Test
    fun aRowSaysWhereTheViewerIsInThatCopy() {
        val film = filmIndexOf(shelves).getValue("HD")
        val rows = versionRowsOf(film, watch(progress = listOf(Progress("HD", 1_800.0, 3_600.0, 1)), watched = listOf(Watched("UHD", 0))), setOf("HD"))
        assertEquals(listOf("Resume at 30:00", "Resume at 30:00"), rows.map(VersionRow::state))
        assertEquals(listOf(false, true), rows.map(VersionRow::held))
        assertEquals(listOf("Resume", "Resume"), rows.map(VersionRow::action))
        assertEquals("Played", versionRowsOf(film, watch(watched = listOf(Watched("UHD", 5))), emptySet()).first().state)
    }

    @Test
    fun aFilmHeldMoreThanOnceLeavesItsRuntimeToTheRows() {
        val film = filmIndexOf(shelves).getValue("HD")
        assertNull(filmHeaderFacts(film.copy(year = null)))
        assertEquals(listOf("Standard · 4K · 1m · 1000 B", "Standard · 1080p · 1m · 1000 B"), filmDetailSections(film).map { it.first })
        assertEquals("2 versions", versionsBadgeOf(film))
        assertNull(versionsBadgeOf(hd))
    }
}

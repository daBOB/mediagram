package catalog

import model.Kind
import model.MediaSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A screen that has opened a title saves its id and resolves it again from
 * whatever the library currently holds, so this lookup is what stands
 * between a saved position and the screen coming back to it. Both halves
 * matter: a film sits on a shelf, an episode sits some depth inside a
 * collection, and the same id has to find either.
 */
class CatalogUiStateTest {

    @Test
    fun aFilmIsFoundOnItsShelf() {
        val alien = film("Alien")

        assertEquals("Alien", readyWith(alien).mediaSet(alien.setId)?.title)
    }

    @Test
    fun anEpisodeIsFoundInsideTheShowThatHoldsIt() {
        val second = episode("30 Rock", season = 7, episode = 2, title = "Frauen sind witzig!")
        val state = readyWith(
            episode("30 Rock", season = 7, episode = 1, title = "Ein Gouverneur zum Totlachen"),
            second,
        )

        assertEquals("Frauen sind witzig!", state.mediaSet(second.setId)?.title)
    }

    /**
     * A lesson nests unevenly — one folder deep in places and four in
     * others — so the walk has to reach every level, not just the first.
     */
    @Test
    fun aLessonIsFoundHoweverDeepItsCourseNests() {
        val moves = lesson("Rust", path = "Basics/Ownership/Borrowing", title = "Moves")

        assertEquals("Moves", readyWith(moves).mediaSet(moves.setId)?.title)
    }

    @Test
    fun anIdTheLibraryNoLongerHoldsResolvesToNothing() {
        assertNull(readyWith(film("Alien")).mediaSet("gone"))
    }

    /**
     * Null while the shelves are still loading is the useful half: the same
     * id resolves a moment later, which is what brings a killed process
     * back to the title it was on rather than dropping it at the catalog.
     */
    @Test
    fun anIdAskedForBeforeTheLibraryArrivesResolvesToNothingYet() {
        assertNull(CatalogUiState.Loading.mediaSet(film("Alien").setId))
    }

    /**
     * The shows table holds one row for a whole series, so whichever
     * episode carries the key carries the key for all of them — which is
     * what lets a show's own screen ask for its synopsis.
     */
    @Test
    fun aShowTakesItsPosterKeyFromWhicheverEpisodeCarriesOne() {
        val shelves = shelvesOf(
            listOf(
                episode("30 Rock", season = 7, episode = 1, title = "One"),
                episode("30 Rock", season = 7, episode = 2, title = "Two", posterKey = "tmdb-tv-4608"),
            ),
        )

        val show = shelves.single { it.title == "Series" }.entries.single() as Entry.Collection
        assertEquals("tmdb-tv-4608", show.posterKey)
    }

    @Test
    fun aCourseNoProviderKnowsCarriesNoKey() {
        val shelves = shelvesOf(listOf(lesson("Rust", path = "Basics", title = "Moves")))

        val course = shelves.single().entries.single() as Entry.Collection
        assertNull(course.posterKey)
    }
}

private fun readyWith(vararg sets: MediaSet) = CatalogUiState.Ready(shelvesOf(sets.toList()))

private fun film(title: String) = set(Kind.MOVIE, title)

private fun episode(
    show: String,
    season: Int,
    episode: Int,
    title: String,
    posterKey: String? = null,
) = set(Kind.EPISODE, title, show = show, season = season, episodeFirst = episode, posterKey = posterKey)

private fun lesson(course: String, path: String, title: String) =
    set(Kind.TUTORIAL, title, show = course, path = path)

private fun set(
    kind: Kind,
    title: String,
    show: String? = null,
    path: String? = null,
    season: Int? = null,
    episodeFirst: Int? = null,
    posterKey: String? = null,
) = MediaSet(
    setId = "$kind-$show-$season-$title",
    kind = kind,
    title = title,
    show = show,
    chapter = null,
    path = path,
    season = season,
    episodeFirst = episodeFirst,
    episodeLast = episodeFirst,
    year = null,
    durationSecs = null,
    posterPath = null,
    totalBytes = 0,
    posterKey = posterKey,
)

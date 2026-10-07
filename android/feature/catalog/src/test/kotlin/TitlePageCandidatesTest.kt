package catalog

import model.Kind
import model.MediaSet
import kotlin.test.Test
import kotlin.test.assertEquals

class TitlePageCandidatesTest {
    private fun film(id: String): MediaSet =
        MediaSet(
            setId = id, kind = Kind.MOVIE, title = id, show = null, chapter = null, path = null,
            season = null, episodeFirst = null, episodeLast = null, year = null, durationSecs = null,
            posterPath = null, totalBytes = 0,
        )

    private fun show(name: String): Entry.Collection =
        Entry.Collection(
            key = "series/$name", kind = CollectionKind.SHOW, name = name, posterPath = null, posterKey = null,
            count = 1, chapters = 1, divisions = emptyList(),
        )

    private fun course(name: String): Entry.Collection =
        Entry.Collection(
            key = "course/$name", kind = CollectionKind.COURSE, name = name, posterPath = null, posterKey = null,
            count = 1, chapters = 1, divisions = emptyList(),
        )

    @Test
    fun everyFilmCollectsMoviesAndAnimeFilmsButSkipsCollectionsAndOtherShelves() {
        val shelves = listOf(
            Shelf(Department.MOVIES, listOf(Entry.Film(film("a")), Entry.Film(film("b")))),
            Shelf(Department.SERIES, listOf(show("Show"))),
            Shelf(Department.ANIME, listOf(Entry.Film(film("c")))),
        )
        assertEquals(listOf("a", "b", "c"), everyFilm(shelves).map { it.setId })
    }

    @Test
    fun showsOfCollectsShowsButNotCoursesOrFilms() {
        val shelves = listOf(
            Shelf(Department.MOVIES, listOf(Entry.Film(film("a")))),
            Shelf(Department.SERIES, listOf(show("Show"))),
            Shelf(Department.TUTORIALS, listOf(course("Course"))),
        )
        assertEquals(listOf("series/Show"), showsOf(shelves).map { it.key })
    }

    /** An anime show is still [CollectionKind.SHOW] — [showsOf] never had to learn about the Anime shelf by name. */
    @Test
    fun showsOfCollectsAnAnimeShowTheSameWayAsAPlainOne() {
        val shelves = listOf(
            Shelf(Department.SERIES, listOf(show("Show"))),
            Shelf(Department.ANIME, listOf(show("Dragonball"))),
        )
        assertEquals(listOf("series/Show", "series/Dragonball"), showsOf(shelves).map { it.key })
    }
}

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
    fun filmsOfCollectsEveryFilmAcrossEveryShelfAndSkipsCollections() {
        val shelves = listOf(
            Shelf("Movies", listOf(Entry.Film(film("a")), Entry.Film(film("b")))),
            Shelf("Series", listOf(show("Show"))),
        )
        assertEquals(listOf("a", "b"), filmsOf(shelves).map { it.setId })
    }

    @Test
    fun showsOfCollectsShowsButNotCoursesOrFilms() {
        val shelves = listOf(
            Shelf("Movies", listOf(Entry.Film(film("a")))),
            Shelf("Series", listOf(show("Show"))),
            Shelf("Tutorials", listOf(course("Course"))),
        )
        assertEquals(listOf("series/Show"), showsOf(shelves).map { it.key })
    }
}

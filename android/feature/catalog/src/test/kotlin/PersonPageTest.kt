package catalog

import model.Kind
import model.MediaSet
import model.Person
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PersonPageTest {
    private fun film(setId: String, posterKey: String?): MediaSet =
        MediaSet(
            setId = setId, kind = Kind.MOVIE, title = setId, show = null, chapter = null, path = null,
            season = null, episodeFirst = null, episodeLast = null, year = null, durationSecs = null,
            posterPath = null, totalBytes = 0, posterKey = posterKey,
        )

    private fun show(name: String, posterKey: String?): Entry.Collection {
        val first = MediaSet(
            setId = "$name-e1", kind = Kind.EPISODE, title = "E1", show = name, chapter = null, path = null,
            season = 1, episodeFirst = 1, episodeLast = null, year = null, durationSecs = null,
            posterPath = null, totalBytes = 0, posterKey = posterKey,
        )
        return Entry.Collection(
            key = "series/$name", kind = CollectionKind.SHOW, name = name, posterPath = null, posterKey = posterKey,
            count = 1, chapters = 1, divisions = listOf(Division(name, 1, listOf(first), emptyList())),
        )
    }

    @Test
    fun collectsFilmsAndShowsCreditedUnderThePersonsTitleKeys() {
        val shelves = listOf(
            Shelf("Movies", listOf(Entry.Film(film("f1", "tmdb-movie-1")), Entry.Film(film("f2", "tmdb-movie-2")))),
            Shelf("Series", listOf(show("Breaking Bad", "tmdb-tv-1"))),
        )
        val person = Person(personId = 1, name = "Bryan Cranston", portraitPath = null, titleKeys = listOf("tmdb-movie-1", "tmdb-tv-1"))

        val page = personPageOf(person, shelves)

        assertEquals(listOf("f1"), page?.films?.map(MediaSet::setId))
        assertEquals(listOf("series/Breaking Bad"), page?.shows?.map(Entry.Collection::key))
    }

    @Test
    fun nobodyVisibleAnswersNullRatherThanAnEmptyPage() {
        val shelves = listOf(Shelf("Movies", listOf(Entry.Film(film("f1", "tmdb-movie-1")))))
        val person = Person(personId = 1, name = "Nobody Here", portraitPath = null, titleKeys = listOf("tmdb-movie-9"))
        assertNull(personPageOf(person, shelves))
    }

    @Test
    fun noPersonAtAllAnswersNull() {
        assertNull(personPageOf(null, emptyList()))
    }
}

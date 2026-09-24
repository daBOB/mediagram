package catalog

import model.Kind
import model.MediaSet
import kotlin.test.Test
import kotlin.test.assertEquals

/** Mirrors `web/test/genres.test.ts`, case for case. */
class GenreShelfTest {

    private val shelves = listOf(
        Shelf(
            "Movies",
            listOf(
                film("Logan Lucky", listOf("Komödie", "Krimi")),
                film("Looper", listOf("Action", "Thriller")),
                film("Untagged", emptyList()),
            ),
        ),
        Shelf("Series", listOf(show("30 Rock", listOf("Komödie")), show("Star City", listOf("Drama")))),
    )

    @Test
    fun holdsTheFilmsAndTheSeriesTaggedWithIt() {
        val shelf = genreShelf(shelves, "Komödie")
        assertEquals(listOf("Logan Lucky"), shelf.films.map { it.set.title })
        assertEquals(listOf("30 Rock"), shelf.series.map { it.name })
    }

    @Test
    fun matchesTheNameExactlyAsTheUploaderStoredIt() {
        assertEquals(GenreShelf(emptyList(), emptyList()), genreShelf(shelves, "komödie"))
        assertEquals(GenreShelf(emptyList(), emptyList()), genreShelf(shelves, "Krim"))
    }

    @Test
    fun aTitleWithNoGenresRecordedIsOnNoShelfAndDoesNotBreakOne() {
        assertEquals(listOf("Looper"), genreShelf(shelves, "Action").films.map { it.set.title })
    }

    private fun film(title: String, genres: List<String>): Entry.Film = Entry.Film(
        MediaSet(
            setId = title,
            kind = Kind.MOVIE,
            title = title,
            show = null,
            chapter = null,
            path = null,
            season = null,
            episodeFirst = null,
            episodeLast = null,
            year = null,
            durationSecs = null,
            posterPath = null,
            totalBytes = 0,
            genres = genres,
        ),
    )

    private fun show(name: String, genres: List<String>): Entry.Collection {
        val episode = MediaSet(
            setId = "$name-1",
            kind = Kind.EPISODE,
            title = "$name 1",
            show = name,
            chapter = null,
            path = null,
            season = 1,
            episodeFirst = 1,
            episodeLast = 1,
            year = null,
            durationSecs = null,
            posterPath = null,
            totalBytes = 0,
            genres = genres,
        )
        return Entry.Collection(
            key = "SHOW/$name",
            kind = CollectionKind.SHOW,
            name = name,
            posterPath = null,
            posterKey = null,
            count = 1,
            chapters = 1,
            divisions = listOf(Division(title = "Season 1", season = 1, items = listOf(episode), children = emptyList())),
        )
    }
}

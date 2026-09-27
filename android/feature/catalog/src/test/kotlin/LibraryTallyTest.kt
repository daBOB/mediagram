package catalog

import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryTallyTest {
    private fun shelf(
        title: String,
        count: Int,
    ) = Shelf(title, List(count) { Entry.Film(film(it)) })

    private fun film(n: Int) =
        model.MediaSet(
            setId = "film-$n",
            kind = model.Kind.MOVIE,
            title = "Film $n",
            show = null,
            chapter = null,
            path = null,
            season = null,
            episodeFirst = null,
            episodeLast = null,
            year = 2020,
            durationSecs = 100,
            posterPath = null,
            totalBytes = 10,
        )

    @Test fun countsPastTwentyAreFigures() {
        assertEquals(listOf("911 films", "43 shows"), libraryTallyLines(listOf(shelf("Movies", 911), shelf("Series", 43))))
    }

    @Test fun countsUpToTwentyAreSpelledWords() {
        assertEquals(listOf("four courses"), libraryTallyLines(listOf(shelf("Tutorials", 4))))
    }

    @Test fun aSingleEntryIsSaidInTheSingular() {
        assertEquals(listOf("one show"), libraryTallyLines(listOf(shelf("Series", 1))))
    }

    @Test fun aShelfThisFunctionDoesNotNameIsLeftOut() {
        assertEquals(emptyList(), libraryTallyLines(listOf(shelf("Documentaries", 5))))
    }
}

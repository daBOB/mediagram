package catalog

import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryTallyTest {
    private fun shelf(
        department: Department,
        count: Int,
    ) = Shelf(department, List(count) { Entry.Film(film(it)) })

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
        assertEquals(listOf("911 films", "43 shows"), libraryTallyLines(listOf(shelf(Department.MOVIES, 911), shelf(Department.SERIES, 43))))
    }

    @Test fun countsUpToTwentyAreSpelledWords() {
        assertEquals(listOf("four courses"), libraryTallyLines(listOf(shelf(Department.TUTORIALS, 4))))
    }

    @Test fun aSingleEntryIsSaidInTheSingular() {
        assertEquals(listOf("one show"), libraryTallyLines(listOf(shelf(Department.SERIES, 1))))
    }

    @Test fun animeAndDocumentariesAreLeftOutAsTheWebRailLeavesThem() {
        assertEquals(emptyList(), libraryTallyLines(listOf(shelf(Department.ANIME, 3), shelf(Department.DOCUMENTARIES, 5))))
    }
}

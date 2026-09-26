package catalog

import model.Kind
import model.MediaSet
import kotlin.test.Test
import kotlin.test.assertEquals

/** Mirrors `web/test/genre-index.test.ts`, case for case. */
class GenreIndexTest {
    private fun title(
        setId: String,
        genres: List<String>,
        popularity: Double,
        backdropPath: String? = "$setId-bg",
    ): MediaSet =
        MediaSet(
            setId = setId, kind = Kind.MOVIE, title = setId, show = null, chapter = null, path = null,
            season = null, episodeFirst = null, episodeLast = null, year = null, durationSecs = null,
            posterPath = null, totalBytes = 0, genres = genres, popularity = popularity, backdropPath = backdropPath,
        )

    @Test
    fun genresBySizeEachPicturedByItsMostPopularTitleNotAlreadyUsed() {
        val index =
            genreIndex(
                listOf(
                    title("zoo", listOf("Animation", "Family"), 99.0),
                    title("up", listOf("Animation", "Family"), 50.0),
                    title("coco", listOf("Animation"), 10.0),
                    title("bare", listOf("Family"), 80.0, backdropPath = null),
                ),
            )
        assertEquals(
            listOf(
                GenreIndexEntry("Animation", 3, "zoo-bg"),
                GenreIndexEntry("Family", 3, "up-bg"),
            ),
            index,
        )
    }

    @Test
    fun aGenreWhoseOnlyPictureIsTakenStillShowsItRatherThanNothing() {
        val art = genreIndex(listOf(title("only", listOf("A", "B"), 1.0))).map { it.art }
        assertEquals(listOf("only-bg", "only-bg"), art)
    }

    @Test
    fun allTitlesIsFilmsPlusEachShowsFirstEpisodeNeverCourses() {
        val film = title("f1", listOf("Drama"), 1.0)
        val episode = MediaSet(
            setId = "e1", kind = Kind.EPISODE, title = "E1", show = "Show", chapter = null, path = null,
            season = 1, episodeFirst = 1, episodeLast = null, year = null, durationSecs = null,
            posterPath = null, totalBytes = 0, genres = listOf("Sci-Fi"),
        )
        val show = Entry.Collection(
            key = "series/Show", kind = CollectionKind.SHOW, name = "Show", posterPath = null, posterKey = null,
            count = 1, chapters = 1, divisions = listOf(Division("Show", 1, listOf(episode), emptyList())),
        )
        val lesson = MediaSet(
            setId = "l1", kind = Kind.TUTORIAL, title = "L1", show = "Course", chapter = null, path = null,
            season = null, episodeFirst = 1, episodeLast = null, year = null, durationSecs = null,
            posterPath = null, totalBytes = 0, genres = listOf("Ignored"),
        )
        val course = Entry.Collection(
            key = "tutorials/Course", kind = CollectionKind.COURSE, name = "Course", posterPath = null, posterKey = null,
            count = 1, chapters = 1, divisions = listOf(Division("Course", null, listOf(lesson), emptyList())),
        )
        val shelves = listOf(
            Shelf("Movies", listOf(Entry.Film(film))),
            Shelf("Series", listOf(show)),
            Shelf("Tutorials", listOf(course)),
        )

        assertEquals(listOf("f1", "e1"), allTitles(shelves).map(MediaSet::setId))
    }
}

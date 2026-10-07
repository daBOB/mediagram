package catalog

import model.Kind
import model.MediaSet
import model.WatchSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [heroArtOf], moved here from `ui.HeroArtOf` (ui-mobile) so the television's
 * own department pages can read the same backdrop the tablet's bar already
 * blends under — its own output is unchanged by the move.
 */
class HeroArtOfTest {
    private fun film(setId: String, backdropPath: String? = null, popularity: Double? = null) =
        MediaSet(
            setId = setId, kind = Kind.MOVIE, title = setId, show = null, chapter = null, path = null,
            season = null, episodeFirst = null, episodeLast = null, year = null, durationSecs = null,
            posterPath = null, totalBytes = 0, backdropPath = backdropPath, popularity = popularity,
        )

    private fun show(name: String, backdropPath: String? = null): Entry.Collection {
        val first = MediaSet(
            setId = "$name-e1", kind = Kind.EPISODE, title = "E1", show = name, chapter = null, path = null,
            season = 1, episodeFirst = 1, episodeLast = null, year = null, durationSecs = null,
            posterPath = null, totalBytes = 0, backdropPath = backdropPath,
        )
        return Entry.Collection(
            key = "series/$name", kind = CollectionKind.SHOW, name = name, posterPath = null, posterKey = null,
            count = 1, chapters = 1, divisions = listOf(Division(name, 1, listOf(first), emptyList())),
        )
    }

    @Test
    fun aDepartmentNotOnTheShelvesAnswersNull() {
        val shelves = listOf(Shelf(Department.MOVIES, listOf(Entry.Film(film("f", backdropPath = "bg", popularity = 1.0)))))
        assertNull(heroArtOf(Department.SERIES, shelves, emptyMap(), WatchSnapshot.Empty))
    }

    @Test
    fun aKeptWallOrCollectionsNamesNoDepartmentAtAll() {
        val shelves = listOf(Shelf(Department.MOVIES, listOf(Entry.Film(film("f", backdropPath = "bg")))))
        assertNull(heroArtOf(null, shelves, emptyMap(), WatchSnapshot.Empty))
    }

    @Test
    fun moviesReadsTheDepartmentsOwnLeadBackdrop() {
        val shelves = listOf(Shelf(Department.MOVIES, listOf(Entry.Film(film("f", backdropPath = "bg", popularity = 1.0)))))
        assertEquals("bg", heroArtOf(Department.MOVIES, shelves, emptyMap(), WatchSnapshot.Empty))
    }

    @Test
    fun seriesReadsTheLeadShowsFirstEpisodeBackdrop() {
        val shelves = listOf(Shelf(Department.SERIES, listOf(show("Breaking Bad", backdropPath = "bd-bg"))))
        assertEquals("bd-bg", heroArtOf(Department.SERIES, shelves, emptyMap(), WatchSnapshot.Empty))
    }

    @Test
    fun tutorialsReadsTheLeadCoursesFirstLessonBackdrop() {
        val shelves = listOf(Shelf(Department.TUTORIALS, listOf(show("Forex", backdropPath = "tut-bg"))))
        assertEquals("tut-bg", heroArtOf(Department.TUTORIALS, shelves, emptyMap(), WatchSnapshot.Empty))
    }

    @Test
    fun animeReadsItsOwnDepartmentsLeadBackdrop() {
        val shelves = listOf(Shelf(Department.ANIME, listOf(Entry.Film(film("your-name", backdropPath = "anime-bg", popularity = 1.0)))))
        assertEquals("anime-bg", heroArtOf(Department.ANIME, shelves, emptyMap(), WatchSnapshot.Empty))
    }

    @Test
    fun documentariesReadsItsOwnDepartmentsLeadBackdrop() {
        val doc = MediaSet(
            setId = "d", kind = Kind.DOCUMENTARY, title = "d", show = null, chapter = null, path = null,
            season = null, episodeFirst = null, episodeLast = null, year = null, durationSecs = null,
            posterPath = null, totalBytes = 0, backdropPath = "doc-bg", addedAt = 1,
        )
        val shelves = listOf(Shelf(Department.DOCUMENTARIES, listOf(Entry.Film(doc))))
        assertEquals("doc-bg", heroArtOf(Department.DOCUMENTARIES, shelves, emptyMap(), WatchSnapshot.Empty))
    }

    @Test
    fun aDepartmentWithNothingToLeadWithAnswersNull() {
        val shelves = listOf(Shelf(Department.MOVIES, listOf(Entry.Film(film("f")))))
        assertNull(heroArtOf(Department.MOVIES, shelves, emptyMap(), WatchSnapshot.Empty))
    }
}

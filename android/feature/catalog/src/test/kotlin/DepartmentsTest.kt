package catalog

import model.Kind
import model.MediaSet
import model.WatchSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DepartmentsTest {
    private fun film(
        setId: String,
        popularity: Double? = null,
        rating: Double? = null,
        backdropPath: String? = null,
        addedAt: Long = 0,
        durationSecs: Int? = null,
    ): MediaSet =
        MediaSet(
            setId = setId, kind = Kind.MOVIE, title = setId, show = null, chapter = null, path = null,
            season = null, episodeFirst = null, episodeLast = null, year = null, durationSecs = durationSecs,
            posterPath = null, totalBytes = 0, popularity = popularity, rating = rating, backdropPath = backdropPath,
            addedAt = addedAt,
        )

    @Test
    fun anEmptyShelfHasNoDepartment() {
        assertNull(moviesDepartmentOf(emptyList(), watched = { false }))
    }

    @Test
    fun theLeadIsTheMostPopularUnwatchedFilmWithABackdropAndItIsExcludedFromFeatured() {
        val dept = moviesDepartmentOf(
            listOf(
                film("watched-favorite", popularity = 99.0, backdropPath = "wf-bg"),
                film("no-art", popularity = 90.0),
                film("lead", popularity = 50.0, backdropPath = "lead-bg"),
                film("second", popularity = 10.0, backdropPath = "second-bg"),
            ),
            watched = { it == "watched-favorite" },
        )!!
        assertEquals("lead", dept.lead?.setId)
        assertEquals(listOf("no-art", "second"), dept.featured.map(MediaSet::setId))
    }

    @Test
    fun acclaimedIsUnwatchedAndAtOrAboveSevenPointFive() {
        val dept = moviesDepartmentOf(
            listOf(film("great", rating = 8.0), film("fine", rating = 7.0), film("seen", rating = 9.0)),
            watched = { it == "seen" },
        )!!
        assertEquals(listOf("great"), dept.acclaimed.map(MediaSet::setId))
    }

    @Test
    fun recentlyAddedIsNewestFirstAcrossEveryFilmRegardlessOfWatched() {
        val dept = moviesDepartmentOf(
            listOf(film("old", addedAt = 1), film("new", addedAt = 2)),
            watched = { it == "new" },
        )!!
        assertEquals(listOf("new", "old"), dept.recentlyAdded.map(MediaSet::setId))
    }

    @Test
    fun hoursSumsEveryFilmsDurationRegardlessOfWatched() {
        val dept = moviesDepartmentOf(listOf(film("a", durationSecs = 3600), film("b", durationSecs = 7200)), watched = { false })!!
        assertEquals(3, dept.hours)
    }

    private fun show(name: String, popularity: Double? = null, backdropPath: String? = null, addedAt: Long = 0): Entry.Collection {
        val first = MediaSet(
            setId = "$name-e1", kind = Kind.EPISODE, title = "E1", show = name, chapter = null, path = null,
            season = 1, episodeFirst = 1, episodeLast = null, year = null, durationSecs = null,
            posterPath = null, totalBytes = 0, popularity = popularity, backdropPath = backdropPath, addedAt = addedAt,
        )
        return Entry.Collection(
            key = "series/$name", kind = CollectionKind.SHOW, name = name, posterPath = null, posterKey = null,
            count = 1, chapters = 1, divisions = listOf(Division(name, 1, listOf(first), emptyList())),
        )
    }

    @Test
    fun anEmptyShowShelfHasNoDepartment() {
        assertNull(showsDepartmentOf(Kind.EPISODE, emptyList(), emptyMap(), WatchSnapshot.Empty))
    }

    @Test
    fun belowTheRowSizePopularAndNewEpisodesAreEmptyButAllListsEveryShow() {
        val shows = listOf(show("Show A"), show("Show B"))
        val dept = showsDepartmentOf(Kind.EPISODE, shows, emptyMap(), WatchSnapshot.Empty)!!
        assertTrue(dept.popular.isEmpty())
        assertTrue(dept.newEpisodes.isEmpty())
        assertEquals(2, dept.all.size)
    }

    @Test
    fun aboveTheRowSizePopularAndNewEpisodesRank() {
        val shows = (1..13).map { show("Show $it", popularity = it.toDouble(), addedAt = it.toLong()) }
        val dept = showsDepartmentOf(Kind.EPISODE, shows, emptyMap(), WatchSnapshot.Empty)!!
        assertEquals("Show 13", dept.popular.first().name)
        assertEquals("Show 13", dept.newEpisodes.first().name)
    }

    @Test
    fun theLeadIsTheMostPopularShowWithABackdrop() {
        val shows = listOf(show("No Art", popularity = 99.0), show("Lead", popularity = 10.0, backdropPath = "lead-bg"))
        val dept = showsDepartmentOf(Kind.EPISODE, shows, emptyMap(), WatchSnapshot.Empty)!!
        assertEquals("Lead", dept.lead?.name)
    }
}

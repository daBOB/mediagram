package catalog

import model.MediaSet
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every department hero's own figures line spells a count of twenty or
 * fewer and reads figures past it, the parity fix this file exists for —
 * before it, only [documentariesLineOf] (moved here unchanged) already did.
 */
class DepartmentLinesTest {
    private fun dept(filmCount: Int, hours: Int) =
        MoviesDepartment(filmCount = filmCount, hours = hours, lead = null, featured = emptyList(), genres = emptyList(), acclaimed = emptyList(), recentlyAdded = emptyList())

    @Test
    fun moviesSpellsAFilmCountOfTwentyOrFewer() {
        assertEquals("four films", moviesLineOf(dept(filmCount = 4, hours = 0)))
    }

    @Test
    fun moviesReadsFiguresPastTwentyAndFormatsHoursWithThousands() {
        assertEquals("911 films · 1,523 hours", moviesLineOf(dept(filmCount = 911, hours = 1523)))
    }

    @Test
    fun moviesDropsTheHoursHalfWhenThereAreNone() {
        assertEquals("four films", moviesLineOf(dept(filmCount = 4, hours = 0)))
    }

    private fun showsDept(showCount: Int, itemCount: Int) =
        ShowsDepartment(
            showCount = showCount, itemCount = itemCount, lead = null,
            underway = Underway(emptyList(), emptyList(), 0, 0), categories = emptyList(),
            popular = emptyList(), newEpisodes = emptyList(), all = emptyList(),
        )

    @Test
    fun seriesSpellsBothHalvesBelowTheirOwnCeiling() {
        assertEquals("four shows · seventeen episodes", showsLineOf(showsDept(4, 17), Department.SERIES))
    }

    @Test
    fun tutorialsNamesItsShowsCoursesAndReadsFiguresPastTwenty() {
        assertEquals("48 courses · 511 lessons", showsLineOf(showsDept(48, 511), Department.TUTORIALS))
    }

    private fun animeDept(showCount: Int, filmCount: Int) =
        AnimeDepartment(showCount = showCount, filmCount = filmCount, lead = null, continuing = emptyList(), nextUp = emptyList(), shows = emptyList(), films = emptyList())

    @Test
    fun animeJoinsBothHalvesWhenNeitherIsZero() {
        assertEquals("one show · two films", animeLineOf(animeDept(1, 2)))
    }

    @Test
    fun animeDropsTheShowsHalfWhenThereAreNone() {
        assertEquals("two films", animeLineOf(animeDept(0, 2)))
    }

    @Test
    fun animeDropsTheFilmsHalfWhenThereAreNone() {
        assertEquals("one show", animeLineOf(animeDept(1, 0)))
    }

    @Test
    fun documentariesSpellsBelowTheCeilingAndReadsFiguresPastIt() {
        val film = MediaSet(setId = "d", kind = model.Kind.DOCUMENTARY, title = "d", show = null, chapter = null, path = null, season = null, episodeFirst = null, episodeLast = null, year = null, durationSecs = null, posterPath = null, totalBytes = 0)
        val library = DocumentaryLibrary(collections = emptyList(), singles = listOf(film))
        val dept = documentariesDepartmentOf(library, emptyMap(), model.WatchSnapshot.Empty)!!
        assertEquals("one documentary", documentariesLineOf(dept))
    }

    @Test
    fun collectionsDropsTheFranchiseHalfWhenThereAreNoneButAlwaysNamesTheLists() {
        assertEquals("zero lists", collectionsLineOf(franchiseCount = 0, listCount = 0))
        assertEquals("three franchises · one list", collectionsLineOf(franchiseCount = 3, listCount = 1))
    }
}

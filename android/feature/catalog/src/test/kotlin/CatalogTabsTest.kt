package catalog

import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogTabsTest {
    @Test
    fun mastheadSplitPutsCollectionsInTheDepartmentRowAndEveryUtilityOnceInTheOverflow() {
        val shelves = listOf(Shelf("Movies", emptyList()), Shelf("Series", emptyList()), Shelf("Tutorials", emptyList()))

        val split = mastheadSplitOf(shelves)

        assertEquals(listOf("Home", "Movies", "Series", "Tutorials", "Collections"), split.departments)
        assertEquals(
            listOf("My List", "Continue watching", "Latest", "Genres", "Settings"),
            split.utilities.map(UtilityDestination::label),
        )
    }

    /** [DOCUMENTARIES] is a real shelf now, so both readers place it the same way any other shelf lands — between Series and Tutorials, the order [shelvesOf] itself returns them in. */
    @Test
    fun documentariesSitsBetweenSeriesAndTutorialsInBothTabRowsAndCountsInFirstKept() {
        val shelves = listOf(Shelf("Movies", emptyList()), Shelf("Series", emptyList()), Shelf(DOCUMENTARIES, emptyList()), Shelf("Tutorials", emptyList()))

        val tabs = catalogTabsOf(shelves)
        assertEquals(listOf("Home", "Movies", "Series", DOCUMENTARIES, "Tutorials", "Continue", "My List", "Collections"), tabs.titles)
        assertEquals(5, tabs.firstKept)

        val split = mastheadSplitOf(shelves)
        assertEquals(listOf("Home", "Movies", "Series", DOCUMENTARIES, "Tutorials", "Collections"), split.departments)
    }

    /** [ANIME] lands the same way — a real shelf `shelvesOf` returns between Series and Documentaries, and neither reader has to name it specially. */
    @Test
    fun animeSitsBetweenSeriesAndDocumentariesInBothTabRows() {
        val shelves = listOf(Shelf("Movies", emptyList()), Shelf("Series", emptyList()), Shelf(ANIME, emptyList()), Shelf(DOCUMENTARIES, emptyList()))

        val tabs = catalogTabsOf(shelves)
        assertEquals(listOf("Home", "Movies", "Series", ANIME, DOCUMENTARIES, "Continue", "My List", "Collections"), tabs.titles)

        val split = mastheadSplitOf(shelves)
        assertEquals(listOf("Home", "Movies", "Series", ANIME, DOCUMENTARIES, "Collections"), split.departments)
    }
}

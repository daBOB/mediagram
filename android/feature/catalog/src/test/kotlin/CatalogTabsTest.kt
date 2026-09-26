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
}

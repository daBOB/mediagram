package catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CatalogTabsTest {
    private val everyDepartment = Department.entries.map { Shelf(it, emptyList()) }

    /** The web's `nav.departments` order: Home, the shelves in the order [shelvesOf] returns them, then Collections. */
    @Test
    fun theMastheadIsHomeThenEachShelfThenCollections() {
        assertEquals(
            listOf("Home", "Movies", "Series", "Anime", "Documentaries", "Tutorials", "Collections"),
            mastheadTabsOf(everyDepartment).map(CatalogTab::label),
        )
    }

    /** Continue and My List are the rail's own rows, never pills. */
    @Test
    fun theMastheadLeavesContinueAndMyListToTheRail() {
        val masthead = mastheadTabsOf(everyDepartment)

        assertFalse(CatalogTab.Kept(KeptKind.CONTINUE) in masthead)
        assertFalse(CatalogTab.Kept(KeptKind.WATCHLIST) in masthead)
    }

    /** The keys are the web's own section ids, so a saved key means the same tab on both surfaces. */
    @Test
    fun everyTabIsFoundAgainByItsKey() {
        val tabs = listOf(CatalogTab.Home) + everyDepartment.map { CatalogTab.Dept(it.department) } + KeptKind.entries.map(CatalogTab::Kept)

        assertEquals(
            listOf("home", "movies", "series", "anime", "documentaries", "tutorials", "continue", "watchlist", "collections"),
            tabs.map(CatalogTab::key),
        )
        for (tab in tabs) assertEquals(tab, catalogTabOf(tab.key, everyDepartment))
    }

    /** A key this build has never written, or a save from before a refresh, lands on Home rather than on whatever now sits where the tab was. */
    @Test
    fun anUnknownKeyOrADepartmentNoLongerShelvedOpensHome() {
        val withoutAnime = everyDepartment.filterNot { it.department == Department.ANIME }

        assertEquals(CatalogTab.Home, catalogTabOf("nonsense", everyDepartment))
        assertEquals(CatalogTab.Home, catalogTabOf("anime", withoutAnime))
        assertEquals(CatalogTab.Dept(Department.DOCUMENTARIES), catalogTabOf("documentaries", withoutAnime))
    }
}

package ui.catalog.browse

import catalog.CatalogTabs
import kotlin.test.Test
import kotlin.test.assertEquals
import ui.catalog.visibleTabIndices

/**
 * The masthead's own departments-only tab row over [catalog.catalogTabsOf]'s
 * full index space (Home, each shelf, then Continue, Watchlist,
 * Collections) — every destination named in web 0.62.1's own
 * `nav.departments` exactly once, Continue and Watchlist left for the
 * overflow menu's utilities to reach instead.
 */
class VisibleTabIndicesTest {
    @Test
    fun everyDepartmentIsVisibleExactlyOnceAndKeptTabsAreHidden() {
        val tabs = CatalogTabs(titles = listOf("Home", "Movies", "Series", "Tutorials", "Continue", "Watchlist", "Collections"), firstKept = 4)

        val visible = visibleTabIndices(tabs)

        assertEquals(listOf(0, 1, 2, 3, 6), visible)
        assertEquals(listOf("Home", "Movies", "Series", "Tutorials", "Collections"), visible.map(tabs.titles::get))
    }

    @Test
    fun aSingleShelfLibraryStillHidesContinueAndWatchlistOnly() {
        val tabs = CatalogTabs(titles = listOf("Home", "Series", "Continue", "Watchlist", "Collections"), firstKept = 2)

        val visible = visibleTabIndices(tabs)

        assertEquals(listOf("Home", "Series", "Collections"), visible.map(tabs.titles::get))
    }
}

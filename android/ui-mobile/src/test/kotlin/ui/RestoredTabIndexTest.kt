package ui

import catalog.CatalogTabs
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Inserting a department shifts every tab after it — Documentaries landed
 * between Series and Tutorials, which used to sit at index 3 and now sits
 * at 4. A tab chosen before that shift and restored by its old, plain index
 * would reopen on Documentaries instead; restoring by title is what a saved
 * choice actually survives the shift for.
 */
class RestoredTabIndexTest {
    private val shifted = CatalogTabs(
        titles = listOf("Home", "Movies", "Series", "Documentaries", "Tutorials", "Continue", "Watchlist", "Collections"),
        firstKept = 5,
    )

    @Test
    fun aTitleStillInTheTabsRestoresToItsNewIndex() {
        assertEquals(4, restoredTabIndex(shifted, "Tutorials"))
    }

    @Test
    fun aTitleThisBuildNoLongerHasFallsBackToHome() {
        assertEquals(0, restoredTabIndex(shifted, "Some Removed Department"))
    }

    @Test
    fun nothingChosenYetIsHome() {
        assertEquals(0, restoredTabIndex(shifted, "Home"))
    }
}

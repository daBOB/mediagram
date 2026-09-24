package ui.catalog

import androidx.window.core.layout.WindowWidthSizeClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shelf row must actually show more posters on a wider window, not
 * merely claim to: this pins [posterColumnsFor] to the three real
 * `WindowWidthSizeClass` buckets Android reports, so a tablet's wider
 * class produces a visibly denser row than a phone's.
 */
class CatalogScreenDensityTest {
    @Test
    fun aWiderWindowShowsMorePostersPerRow() {
        val compact = posterColumnsFor(WindowWidthSizeClass.COMPACT)
        val medium = posterColumnsFor(WindowWidthSizeClass.MEDIUM)
        val expanded = posterColumnsFor(WindowWidthSizeClass.EXPANDED)

        assertTrue(compact < medium, "medium should be denser than compact")
        assertTrue(medium < expanded, "expanded should be denser than medium")
    }

    @Test
    fun columnCountsMatchTheDesignedDensity() {
        assertEquals(3, posterColumnsFor(WindowWidthSizeClass.COMPACT))
        assertEquals(4, posterColumnsFor(WindowWidthSizeClass.MEDIUM))
        assertEquals(6, posterColumnsFor(WindowWidthSizeClass.EXPANDED))
    }
}

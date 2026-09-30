package ui.tv.catalog

import org.junit.Test
import kotlin.test.assertEquals

/**
 * [sectionCrossingsOf]'s own arithmetic, proven directly rather than
 * through a real focus move: Robolectric's own two-dimensional focus
 * search already happens to land on the same column for this shape of
 * grid without any of this wiring at all, so a Compose-level test here
 * could not tell a working rule from a missing one — see
 * `TvWallStateTest.upAndDownAcrossAHeadingKeepTheColumnRatherThanJumpingToTheFarRowsLastItem`'s
 * own doc. What a real remote reached on the box (the row above's own
 * last column, not the same one) is exactly what this function exists to
 * override regardless of what any one search implementation happens to
 * do, so this pins the rule the override enforces, not whether Robolectric
 * needed it today.
 */
class TvWallCellsTest {
    /** The box's own repro: ten "Series" items (a short four-item last row), then six "Films". */
    @Test
    fun aColumnPastTheShortRowAboveClampsToItsOwnLastItem() {
        val crossings = sectionCrossingsOf(itemCount = 16, headings = mapOf(0 to "Series", 10 to "Films"))

        // Films' own first row, columns 0-3: the same column exists in
        // Series' own last row (items 6-9), so Up reaches it exactly.
        assertEquals(6, crossings.up[10])
        assertEquals(7, crossings.up[11])
        assertEquals(8, crossings.up[12])
        assertEquals(9, crossings.up[13])
        // Columns 4 and 5 do not exist in Series' own four-item last row —
        // Dragonball's own row — clamping to its own last item (9, "Mila
        // Superstar") rather than the box's own bug (jumping to it for
        // every column, including the ones that did have an exact match).
        assertEquals(9, crossings.up[14])
        assertEquals(9, crossings.up[15])

        // The same column back down, for every one of Series' own last row.
        assertEquals(10, crossings.down[6])
        assertEquals(11, crossings.down[7])
        assertEquals(12, crossings.down[8])
        assertEquals(13, crossings.down[9])
    }

    @Test
    fun oneSectionNeedsNoWiringAtAll() {
        val crossings = sectionCrossingsOf(itemCount = 12, headings = emptyMap())

        assertEquals(emptyMap(), crossings.up)
        assertEquals(emptyMap(), crossings.down)
    }

    @Test
    fun anEmptyWallNeedsNoWiringEither() {
        val crossings = sectionCrossingsOf(itemCount = 0, headings = mapOf(0 to "Series"))

        assertEquals(emptyMap(), crossings.up)
        assertEquals(emptyMap(), crossings.down)
    }
}

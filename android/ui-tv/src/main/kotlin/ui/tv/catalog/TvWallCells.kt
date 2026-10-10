package ui.tv.catalog

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One line of a wall as the grid lays it out: the optional header across
 * the top, a section's label, or one plate — so a plate's place in the grid
 * is looked up here rather than worked out again wherever the grid has to be
 * scrolled to one.
 */
internal sealed interface WallCell {
    data object Header : WallCell

    data class Heading(val label: String) : WallCell

    data class Plate(val index: Int) : WallCell
}

internal fun cellsOf(
    items: List<*>,
    hasHeader: Boolean,
    headings: Map<Int, String>,
): List<WallCell> =
    buildList {
        if (hasHeader) add(WallCell.Header)
        items.indices.forEach { index ->
            headings[index]?.let { add(WallCell.Heading(it)) }
            add(WallCell.Plate(index))
        }
    }

/** [sectionCrossingsOf]'s own result: `up`/`down` are global plate indices — this wall's own [WallCell.Plate.index] — mapped to the global plate index a D-pad press across a section boundary must land on instead. */
internal data class SectionCrossings(val up: Map<Int, Int>, val down: Map<Int, Int>)

/**
 * The up/down targets [TvWall]'s own grid has to wire by hand across a
 * heading: every other row's default two-dimensional focus search already
 * keeps the column, since two adjacent rows of plates are the same shape,
 * but the heading between two sections is a full-width span, and crossing
 * it lands on whichever plate composition happened to place right before
 * or after it — the far row's own last column, not the same one — rather
 * than by column. The rule a viewer actually wants crossing it: the same
 * column on the far side, or that row's own last item when it is shorter.
 *
 * A section is a run of plates with no heading inside it; [headings]' own
 * keys are where a new one starts (see [cellsOf] — a heading always forces
 * a fresh row on both sides of itself, so a section's own first row is
 * never partial). Only a section's own last row and the next section's own
 * first row ever need wiring — every other row-to-row move within one
 * section is a plain, evenly-spaced grid move Compose's own search already
 * gets right.
 */
internal fun sectionCrossingsOf(
    itemCount: Int,
    headings: Map<Int, String>,
    columns: Int = Columns,
): SectionCrossings {
    if (itemCount == 0) return SectionCrossings(emptyMap(), emptyMap())
    val starts = (headings.keys.filter { it in 0 until itemCount } + 0).distinct().sorted()
    val sections = starts.mapIndexed { i, start -> start until starts.getOrElse(i + 1) { itemCount } }
    val up = mutableMapOf<Int, Int>()
    val down = mutableMapOf<Int, Int>()
    for (i in 0 until sections.size - 1) {
        val above = sections[i]
        val below = sections[i + 1]
        val aboveRowStart = above.first + (above.count() - 1) / columns * columns
        val aboveRow = (aboveRowStart..above.last).toList()
        val belowRow = (below.first..minOf(below.last, below.first + columns - 1)).toList()
        belowRow.forEachIndexed { column, index -> up[index] = aboveRow[minOf(column, aboveRow.size - 1)] }
        aboveRow.forEachIndexed { column, index -> down[index] = belowRow[minOf(column, belowRow.size - 1)] }
    }
    return SectionCrossings(up, down)
}

/**
 * The space above [cell] in a wall, carried by each cell rather than by the grid's arrangement,
 * which would put one gap around the head, a section's heading and the lines alike. Between two
 * lines of a section, the grid's line gap; under the head, `.shelf-head`'s margin; around a
 * heading, `.shelf-sub`'s 40 above and 20 below (`catalog.css:11,98`).
 */
internal fun spaceAbove(
    cell: WallCell,
    hasHeader: Boolean,
    headings: Map<Int, String>,
    columns: Int,
): Dp =
    when (cell) {
        WallCell.Header -> 0.dp
        is WallCell.Heading -> if (!hasHeader && headings[0] == cell.label) 0.dp else ShelfSubAbove
        is WallCell.Plate -> {
            val sectionStart = headings.keys.filter { it <= cell.index }.maxOrNull()
            when {
                cell.index - (sectionStart ?: 0) >= columns -> if (columns == Columns) WallLineGap else TileGap
                sectionStart != null -> ShelfSubBelow
                hasHeader -> ShelfHeadBelow
                else -> 0.dp
            }
        }
    }

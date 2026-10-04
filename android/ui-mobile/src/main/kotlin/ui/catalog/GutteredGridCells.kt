package ui.catalog

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * [columns] cells across a page that opens on a full-bleed hero and goes on
 * into plates — a department, a franchise. The plates belong inside the
 * page's side [gutter], where its row headings and strips already sit, while
 * the hero still reaches both screen edges; `contentPadding` would inset the
 * hero too. So the gutter lives in the two outer cells instead: they are
 * [gutter] wider, and [gutteredCell] pads the plate in each back in by it,
 * leaving every plate one width.
 */
internal data class GutteredCells(
    private val columns: Int,
    private val gutter: Dp,
) : GridCells {
    override fun Density.calculateCrossAxisCellSizes(
        availableSize: Int,
        spacing: Int,
    ): List<Int> {
        val edge = gutter.roundToPx()
        val inner = (availableSize - 2 * edge - spacing * (columns - 1)).coerceAtLeast(0)
        return List(columns) { column ->
            inner / columns +
                (if (column < inner % columns) 1 else 0) +
                (if (column == 0) edge else 0) +
                (if (column == columns - 1) edge else 0)
        }
    }
}

/**
 * Takes [GutteredCells]' gutter back out of the plate at [index] in a run of
 * plates — a run that starts on a fresh line, as every run under a full-width
 * heading does.
 */
internal fun Modifier.gutteredCell(
    index: Int,
    columns: Int,
    gutter: Dp,
): Modifier {
    val column = index % columns
    return padding(start = if (column == 0) gutter else 0.dp, end = if (column == columns - 1) gutter else 0.dp)
}

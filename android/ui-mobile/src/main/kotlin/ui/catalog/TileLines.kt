package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** `.destinations{gap:16px}`, and the gap between the lines they wrap onto. */
private val TileGap = 16.dp

/** As many equal columns of at least [minWidth] as fit across [width] — CSS's `repeat(auto-fill, minmax(min, 1fr))`. */
internal fun tileColumnsOf(
    width: Dp,
    minWidth: Dp,
): Int = ((width + TileGap) / (minWidth + TileGap)).toInt().coerceAtLeast(1)

/**
 * [items] as lines of [columns] tiles, each line its own item of the page's
 * lazy list: a library's worth of franchises composed as one item was every
 * card built and held at once — a stutter on opening Collections, and memory
 * spent on cards nobody had scrolled to. [section] keeps two runs of lines
 * on one page apart; [id] keys each tile by what it stands for, so a
 * reorder moves the tile rather than handing its slot to whatever moved in.
 */
internal fun <T> LazyListScope.tileLines(
    section: String,
    items: List<T>,
    columns: Int,
    id: (T) -> Any,
    modifier: Modifier = Modifier,
    tile: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    items.chunked(columns).forEachIndexed { index, line ->
        item(key = "$section-line:$index") { TileLine(line, columns, id, modifier.padding(top = if (index == 0) 0.dp else TileGap), tile) }
    }
}

/**
 * Tiles wrapped into as many equal columns of at least [minWidth] as fit,
 * all composed at once — for a section that is one item of a page's own
 * lazy list and holds a handful (Search's Collections part), where a nested
 * lazy grid could not be measured. A whole page of them is [tileLines].
 */
@Composable
internal fun <T> TileFlow(
    items: List<T>,
    minWidth: Dp,
    id: (T) -> Any,
    modifier: Modifier = Modifier,
    tile: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    BoxWithConstraints(modifier) {
        val columns = tileColumnsOf(maxWidth, minWidth)
        Column(verticalArrangement = Arrangement.spacedBy(TileGap)) {
            for (line in items.chunked(columns)) TileLine(line, columns, id, Modifier, tile)
        }
    }
}

/**
 * One line of tiles sharing the width equally; a short last line keeps the
 * others' widths, as the web's `auto-fill` tracks do. Weighted rather than
 * worked out in pixels, so a line of them and its gaps never overshoots by a
 * rounding and drops its last tile.
 */
@Composable
private fun <T> TileLine(
    line: List<T>,
    columns: Int,
    id: (T) -> Any,
    modifier: Modifier,
    tile: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
        for (item in line) key(id(item)) { tile(item, Modifier.weight(1f)) }
        repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
    }
}

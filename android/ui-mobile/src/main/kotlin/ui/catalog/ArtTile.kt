package ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import designsystem.Radius
import ui.catalog.home.OnImage
import ui.catalog.home.fluid
import java.io.File
import kotlin.math.roundToInt

/** `.destinations{grid-template-columns:repeat(auto-fill,minmax(16rem,1fr))}`. */
internal val DESTINATION_MIN_WIDTH = 256.dp

/**
 * A picture card that names what it opens across its own art — the web's
 * `.genre-tile` and `.destination` (`genreTiles` in `utility-pages.js`,
 * `destination` in `collections-page.js`), one composable because the two
 * differ only in proportion and in how loudly the name is set.
 *
 * Unlike a plate, the name sits on the face: a tile stands for a group —
 * a genre, a franchise, a list — whose art is borrowed from one member, so
 * nothing on the picture already says what the tile is. With no art at all
 * the tile is the page's sunk ground with the name in ink, as the web's
 * `:not(:has(img))` rule draws it.
 *
 * [aspectRatio] is the tile's shape, and a floor rather than a cage: a name
 * set large by the system's font size, or wrapping to a second line, grows
 * the tile rather than cutting its count off the foot.
 *
 * [destination] sets the name as `.destination-name` does — larger and in
 * capitals — rather than as `.genre-tile-name`. The web's hover zoom has no
 * touch counterpart and is left out.
 */
@Composable
internal fun ArtTile(
    name: String,
    meta: String,
    art: String?,
    aspectRatio: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destination: Boolean = false,
) {
    val ink = if (art != null) OnImage else MaterialTheme.colorScheme.onSurface
    Box(
        modifier =
            modifier
                .aspectRatioAtLeast(aspectRatio)
                .clip(RoundedCornerShape(Radius.card))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {},
    ) {
        if (art != null) {
            AsyncImage(model = File(art), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            ArtTileScrim(destination, Modifier.matchParentSize())
        }
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = if (destination) 22.dp else 18.dp, vertical = if (destination) 20.dp else 16.dp),
        ) {
            val nameStyle =
                if (destination) {
                    val width = LocalConfiguration.current.screenWidthDp.toFloat()
                    MaterialTheme.typography.headlineSmall.copy(fontSize = fluid(22.4f, 0.02f, 28.8f, width).sp, lineHeight = 1.05.em, letterSpacing = (-0.015).em)
                } else {
                    MaterialTheme.typography.headlineSmall.copy(fontSize = 21.6.sp, lineHeight = 1.1.em, letterSpacing = (-0.01).em)
                }
            Text(
                text = if (destination) name.uppercase() else name,
                style = nameStyle,
                color = ink,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = meta,
                style =
                    if (destination) {
                        MaterialTheme.typography.labelMedium
                    } else {
                        MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, letterSpacing = 0.08.em)
                    },
                color = ink.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = if (destination) 6.dp else 4.dp),
            )
        }
    }
}

/**
 * At least as tall as [ratio] makes the width it is given, taller when its
 * content asks for more. A tile is always handed a width (a fixed one in a
 * row, a column's share in a grid), so an unbounded width — which has no
 * proportion to keep — simply measures the content.
 */
private fun Modifier.aspectRatioAtLeast(ratio: Float): Modifier =
    layout { measurable, constraints ->
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
        val floor = (width / ratio).roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
        val placeable = measurable.measure(constraints.copy(minWidth = width, minHeight = floor))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

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

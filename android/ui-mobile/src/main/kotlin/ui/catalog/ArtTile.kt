package ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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

/** A genre tile's proportions on the Genres page (`.genre-tile`, `catalog.css`). */
internal const val GENRE_TILE_ASPECT = 16f / 9f

/** A genre tile's proportions in the Movies department's own row (`.dept-row .genre-tile`, `departments.css`). */
internal const val GENRE_ROW_TILE_ASPECT = 16f / 8f

/** A franchise's or a list's card on Collections and in Search (`.destination`, `departments.css`). */
internal const val DESTINATION_ASPECT = 4f / 3f

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
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(Radius.card))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {},
    ) {
        if (art != null) {
            AsyncImage(model = File(art), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            // `linear-gradient(0deg, dark, light <stop>)`: dark at the foot,
            // easing to the light tone part-way up and holding it to the top.
            val (clear, deep, reach) = if (destination) Triple(0x1A, 0xDB, 0.65f) else Triple(0x1F, 0xD1, 0.7f)
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(0f to scrim(clear), (1f - reach) to scrim(clear), 1f to scrim(deep)),
                ),
            )
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

private fun scrim(alpha: Int) = Color(alpha shl 24 or 0x080809)

/**
 * Tiles wrapped into as many equal columns of at least [minWidth] as fit —
 * CSS's `repeat(auto-fill, minmax(min, 1fr))` — for a section inside a
 * page's own lazy list, where a nested lazy grid could not be measured.
 * The width is worked out in whole pixels so a row of them plus its gaps
 * never overshoots by a rounding and drops its last tile to the next row.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> TileFlow(
    items: List<T>,
    minWidth: Dp,
    modifier: Modifier = Modifier,
    gap: Dp = 16.dp,
    tile: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val gapPx = with(density) { gap.roundToPx() }
        val minPx = with(density) { minWidth.roundToPx() }
        val columns = ((constraints.maxWidth + gapPx) / (minPx + gapPx)).coerceAtLeast(1)
        val tileWidth = with(density) { ((constraints.maxWidth - gapPx * (columns - 1)) / columns).toDp() }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
            maxItemsInEachRow = columns,
        ) {
            for (item in items) tile(item, Modifier.width(tileWidth))
        }
    }
}

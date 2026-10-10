package ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import designsystem.Radius
import designsystem.hoverMotion
import ui.catalog.home.OnImage
import ui.catalog.home.fluid
import ui.chrome.linkClickable
import ui.common.catalog.ArtTileScrim
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
 * capitals — rather than as `.genre-tile-name`. The web's hover zoom
 * follows a hovering mouse or stylus; a finger never zooms the art, which
 * would still be moving after the tap had opened the tile.
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
    val source = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .aspectRatioAtLeast(aspectRatio)
                .clip(RoundedCornerShape(Radius.card))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                // Both are links (`utility-pages.js:76`, `collections-page.js:47`),
                // and links don't shrink.
                .linkClickable(source, onClick = onClick)
                .semantics(mergeDescendants = true) {},
    ) {
        if (art != null) {
            // `.genre-tile:hover img` and `.destination:hover img` zoom to 1.05 over
            // 1100ms (`catalog.css:141,145`, `departments.css:99,103`), inside the clip.
            AsyncImage(
                model = File(art),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().hoverMotion(source, durationMs = 1100, zoom = 1.05f),
            )
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

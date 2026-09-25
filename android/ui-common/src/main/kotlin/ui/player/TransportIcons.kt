package ui.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * The transport's shapes — play, pause, the two skips and next — drawn as
 * paths rather than typed as characters. "⏪", "⏩", "⏸" and "⏭" have an
 * emoji presentation, and Android's font fallback takes it: they came out
 * as orange tiles on both players, beside a gear and an ⓘ that draw as
 * plain monochrome text. Paths draw in whatever colour the button they sit
 * in hands them, so a focused control on the television takes its accent
 * like every other one does.
 *
 * Material's shapes, redrawn here as five paths rather than taken from
 * material-icons-extended: the core icon set has no skip-back or
 * skip-forward, and the extended one is thousands of icons for two.
 */
object TransportIcons {
    val Play: ImageVector = icon("Play") {
        moveTo(8f, 5f)
        verticalLineToRelative(14f)
        lineToRelative(11f, -7f)
        close()
    }

    val Pause: ImageVector = icon("Pause") {
        moveTo(6f, 19f)
        horizontalLineToRelative(4f)
        verticalLineTo(5f)
        horizontalLineTo(6f)
        close()
        moveTo(14f, 5f)
        verticalLineToRelative(14f)
        horizontalLineToRelative(4f)
        verticalLineTo(5f)
        close()
    }

    val SkipBack: ImageVector = icon("SkipBack") {
        moveTo(11f, 18f)
        verticalLineTo(6f)
        lineToRelative(-8.5f, 6f)
        close()
        moveTo(11.5f, 12f)
        lineToRelative(8.5f, 6f)
        verticalLineTo(6f)
        close()
    }

    val SkipForward: ImageVector = icon("SkipForward") {
        moveTo(4f, 18f)
        lineToRelative(8.5f, -6f)
        lineTo(4f, 6f)
        close()
        moveTo(13f, 6f)
        verticalLineToRelative(12f)
        lineToRelative(8.5f, -6f)
        close()
    }

    val Next: ImageVector = icon("Next") {
        moveTo(6f, 18f)
        lineToRelative(8.5f, -6f)
        lineTo(6f, 6f)
        close()
        moveTo(16f, 6f)
        verticalLineToRelative(12f)
        horizontalLineToRelative(2f)
        verticalLineTo(6f)
        close()
    }

    private fun icon(
        name: String,
        shape: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
    ): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply { path(fill = SolidColor(Color.Black), pathBuilder = shape) }
            .build()
}

private const val ICON_TO_TEXT = 4f / 3f

/**
 * One of [TransportIcons] at the size of the text it stands in for — [size]
 * is the font size the glyph was set at, so a surface's type scale still
 * decides how big its transport is — in [tint], the colour the text would
 * have been. Unnamed here: the button around it carries the description, as
 * it did when this was a character.
 */
@Composable
fun TransportIcon(
    icon: ImageVector,
    tint: Color,
    size: TextUnit,
    modifier: Modifier = Modifier,
) {
    // Material's shapes keep a margin inside their 24-unit square, where a
    // character fills its em; a third larger draws them about as tall as
    // the glyphs beside them (the gear, the ⓘ).
    val side = with(LocalDensity.current) { size.toDp() } * ICON_TO_TEXT
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(side),
    )
}

package ui.player

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * The pieces the card is drawn from that hold none of its state: each is
 * handed everything it shows and decides nothing.
 *
 * Apart from the card itself because they have no tie to it — a glyph
 * button is a glyph button wherever it is pressed — and because a file is
 * easier to read when the thing it is named for is the only thing in it.
 */

/** The smallest a control over the picture is ever drawn: a thumb's width, however narrow the window it wraps in. */
internal val MIN_TARGET: Dp = 48.dp

/** Faint enough to read as off, or unavailable, beside a row of white controls. */
private const val DIM_ALPHA = 0.5f

/**
 * A control drawn as a character, named for a screen reader.
 *
 * The name is not decoration: a glyph has no accessible text of its own, so
 * without this the button announces itself as nothing at all.
 */
@Composable
internal fun GlyphButton(
    glyph: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    // A toggle's off state: drawn faint, where a row of white glyphs has no other way to show it.
    dimmed: Boolean = false,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.sizeIn(minWidth = MIN_TARGET, minHeight = MIN_TARGET).semantics { contentDescription = description },
    ) {
        Text(
            text = glyph,
            color = if (dimmed || !enabled) Color.White.copy(alpha = DIM_ALPHA) else Color.White,
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

/**
 * [GlyphButton] for a shape with no reliable character: the transport's
 * play, pause, previous and next, which as text fall back to colour emoji
 * (see [TransportIcons]). Named for a screen reader the same way.
 */
@Composable
internal fun TransportButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.sizeIn(minWidth = MIN_TARGET, minHeight = MIN_TARGET).semantics { contentDescription = description },
    ) {
        TransportIcon(
            icon = icon,
            tint = if (enabled) Color.White else Color.White.copy(alpha = DIM_ALPHA),
            size = MaterialTheme.typography.headlineMedium.fontSize,
        )
    }
}

/** A tool that names what is chosen — `1×`, `Fit`, `Audio` — set smaller than a glyph, since it is a word. */
@Composable
internal fun LabelButton(
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.sizeIn(minWidth = MIN_TARGET, minHeight = MIN_TARGET).semantics { contentDescription = description },
    ) {
        Text(text = label, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun TimeText(text: String) {
    Text(text = text, color = Color.White, style = MaterialTheme.typography.labelLarge)
}

package ui.tv.player

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import designsystem.Palette
import designsystem.Spacing
import designsystem.TvTypeScale
import ui.player.TransportIcon
import ui.player.TransportIcons
import ui.tv.TvFocus

/**
 * A transport control drawn as a character, named for a screen reader —
 * a glyph has no accessible text of its own. A character where one draws
 * plainly — the gear, the ⓘ — and [TvIconButton] where it would not.
 */
@Composable
internal fun TvGlyphButton(
    glyph: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvOverlayButton(
        text = glyph,
        style = TvTypeScale.title,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = description },
    )
}

/**
 * [TvGlyphButton] for the transport's own shapes — play, pause and the two
 * skips — which as characters fall back to colour emoji (see
 * [TransportIcons]). Drawn in the surface's content colour, so focus
 * turns it the accent as it turns a glyph.
 */
@Composable
internal fun TvIconButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvOverlaySurface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics { contentDescription = description },
    ) {
        TransportIcon(
            icon = icon,
            tint = LocalContentColor.current,
            size = TvTypeScale.title.fontSize,
            modifier = Modifier.padding(horizontal = Spacing.large, vertical = Spacing.small),
        )
    }
}

/**
 * Any button drawn over the picture — a transport glyph, a mark's label —
 * in one treatment, so the rows the remote moves between read as one set
 * of controls.
 *
 * The house focus treatment ([TvFocus]) rather than a stock button, so a
 * focused control grows and takes the accent border the way every other
 * focused thing on this surface does. Transparent until focused: over a
 * film, a row of filled chips would be more things to look at. Disabled
 * it dims but stays focusable, so a mark that cannot be pressed can still
 * be read.
 */
@Composable
internal fun TvOverlayButton(
    text: String,
    style: TextStyle,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvOverlaySurface(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text(
            text = text,
            style = style,
            modifier = Modifier.padding(horizontal = Spacing.large, vertical = Spacing.small),
        )
    }
}

/** [TvOverlayButton]'s treatment around any content, for a control that is more than one line of text. */
@Composable
internal fun TvOverlaySurface(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = TvFocus.surfaceShape(),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = Palette.Text,
                focusedContainerColor = Palette.Sunk,
                focusedContentColor = Palette.Imprint,
                pressedContainerColor = Palette.Sunk,
                pressedContentColor = Palette.Imprint,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = Palette.Figures,
            ),
        scale = TvFocus.surfaceScale(),
        border = TvFocus.surfaceBorder(),
        glow = TvFocus.surfaceGlow(),
        content = content,
    )
}

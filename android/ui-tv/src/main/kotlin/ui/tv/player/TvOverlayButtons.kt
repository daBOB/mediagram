package ui.tv.player

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import designsystem.Palette
import designsystem.Spacing
import designsystem.TvTypeScale
import ui.common.player.TransportIcon
import ui.common.player.TransportIcons
import ui.tv.TvFocus

/**
 * A transport control drawn as a character, named for a screen reader —
 * a glyph has no accessible text of its own. A character where one draws
 * plainly — the CC, the ⓘ — and [TvIconButton] where it would not.
 */
@Composable
internal fun TvGlyphButton(
    glyph: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    padding: Dp = Spacing.large,
    dimmed: Boolean = false,
) {
    TvOverlayButton(
        dimmed = dimmed,
        text = glyph,
        style = TvTypeScale.title,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = description },
        padding = padding,
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
            modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.small),
        )
    }
}

/**
 * Any button drawn over the picture — a transport glyph, a mark's label —
 * in one treatment, so the rows the remote moves between read as one set
 * of controls.
 *
 * The house focus treatment ([TvFocus]) rather than a stock button, so a
 * focused control takes the accent border and fill every other focused
 * thing on this surface does — and, over the player, holds its size
 * ([LocalFlatControls]). Transparent until focused: over a
 * film, a row of filled chips would be more things to look at. Disabled
 * it dims but stays focusable, so a mark that cannot be pressed can still
 * be read. [padding] is the room either side of the label: the controls'
 * rows take less than a lone button does, so they still fit a stage the
 * notes column has narrowed.
 */
@Composable
internal fun TvOverlayButton(
    text: String,
    style: TextStyle,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    padding: Dp = Spacing.large,
    dimmed: Boolean = false,
) {
    TvOverlaySurface(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text(
            text = text,
            style = style,
            // The label alone: fading the surface would fade its focus border with it.
            modifier = Modifier.padding(horizontal = padding, vertical = Spacing.small).alpha(if (dimmed) DIMMED_ALPHA else 1f),
        )
    }
}

/** A control that is switched off, drawn quieter but still readable and focusable. */
private const val DIMMED_ALPHA = 0.55f

/**
 * Whether the controls drawn here hold their size under focus. The player
 * sets it: its controls never lift or grow (the web's rule, kept on every
 * surface), while the rest of this surface — and [TvChoiceRow], which the
 * profile screens share — keeps the house treatment's grow ([TvFocus]).
 */
internal val LocalFlatControls = staticCompositionLocalOf { false }

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
                focusedContentColor = MaterialTheme.colorScheme.primary,
                pressedContainerColor = Palette.Sunk,
                pressedContentColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = Palette.Figures,
            ),
        scale = if (LocalFlatControls.current) ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f) else TvFocus.surfaceScale(),
        border = TvFocus.surfaceBorder(),
        glow = TvFocus.surfaceGlow(),
        content = content,
    )
}

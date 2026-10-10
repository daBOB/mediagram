package ui.tv.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import designsystem.Palette
import designsystem.Spacing
import designsystem.tvDp
import ui.common.player.TransportIcon
import ui.common.player.TransportIcons
import ui.tv.TvFocus

/**
 * A control on the card drawn as a character — ↺, −15, ⓘ, CC — named for a
 * screen reader, since a glyph has no accessible text of its own. The
 * tablet's characters in the interface face, not the display serif, at the
 * web's size ([style]: [TvPlayerType.glyph], or a tool's); [TvIconButton]
 * where a character would not draw plainly.
 */
@Composable
internal fun TvGlyphButton(
    glyph: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    padding: Dp = 0.dp,
    dimmed: Boolean = false,
    style: TextStyle = TvPlayerType.glyph,
) {
    TvCardButton(
        text = glyph,
        style = style,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = description },
        padding = padding,
        dimmed = dimmed,
    )
}

/**
 * [TvGlyphButton] for the transport's own shapes — play, pause, ⏮ and ⏭ —
 * which as characters fall back to colour emoji (see [TransportIcons]),
 * at the web's 20px icon in a `.tsp`, or its 28px in `.tsp.big` ([big]) for
 * play and pause. Drawn in the surface's content colour, so focus turns it
 * the accent as it turns a glyph.
 */
@Composable
internal fun TvIconButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    big: Boolean = false,
) {
    val target = if (big) TvBigTarget else TvCardTarget
    TvOverlaySurface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics { contentDescription = description },
    ) {
        Box(modifier = Modifier.sizeIn(minWidth = target, minHeight = target), contentAlignment = Alignment.Center) {
            TransportIcon(
                icon = icon,
                tint = LocalContentColor.current,
                size = (if (big) TvPlayerType.bigGlyph else TvPlayerType.glyph).fontSize,
            )
        }
    }
}

/**
 * A control on the card or in the bar above it, at the web's size: at least
 * [TvCardTarget] either way with its label centred, and [padding] either
 * side of the label — a `.tsp`'s none, a skip's 0.5rem, a `.tool`'s
 * 0.75rem, a `.ghost`'s 0.85rem — in [TvOverlayButton]'s treatment.
 */
@Composable
internal fun TvCardButton(
    text: String,
    style: TextStyle,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    padding: Dp = 0.dp,
    dimmed: Boolean = false,
) {
    TvOverlaySurface(onClick = onClick, enabled = enabled, modifier = modifier) {
        Box(
            modifier = Modifier.sizeIn(minWidth = TvCardTarget, minHeight = TvCardTarget).padding(horizontal = padding),
            contentAlignment = Alignment.Center,
        ) {
            // The label alone: fading the surface would fade its focus border with it.
            Text(text = text, style = style, modifier = Modifier.alpha(if (dimmed) DIMMED_ALPHA else 1f))
        }
    }
}

/**
 * `.tsp`'s 2.75rem (`playback.css:185-186`), `.tool`'s 44px floor
 * (`player-card.css:68-69`) and `.ghost`'s (`playback.css:595`): the least a
 * control on the card or in the bar above it is, either way. A target for
 * the eye on a television, not a finger: the remote reaches it by D-pad.
 */
internal val TvCardTarget = 44.tvDp

/** `.tsp.big`'s 2.9rem (`playback.css:204`): play and pause. */
private val TvBigTarget = 46.4.tvDp

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

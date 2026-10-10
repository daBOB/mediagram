package ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import designsystem.Accent
import designsystem.IndicationTone
import designsystem.Motion
import ui.chrome.CatalogueTextButton
import ui.common.player.TransportIcon
import ui.common.player.TransportIcons

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
 * The web's `--stage-accent` (`theme.css:97`): the default accent's dark
 * value, whatever accent the page wears, because the stage is always dark.
 */
internal val StageAccent: Color = Accent.Default.resolve(dark = true)

/**
 * How a press looks over the video. White ink, as the web fills its tools
 * with 8% white (`player-card.css:82`, `playback.css:200`), where the page's
 * ink would vanish into a dark frame in the light theme. The focus ring is in
 * the stage's accent (`playback.css:20`).
 */
internal val OverVideoTone = IndicationTone(ink = Color.White, ring = StageAccent)

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
    /** What a screen reader says of a toggle or an opener — "On", "Expanded" — beyond its name; null for a plain button. */
    state: String? = null,
) {
    val source = remember { MutableInteractionSource() }
    CatalogueTextButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = source,
        modifier = modifier.sizeIn(minWidth = MIN_TARGET, minHeight = MIN_TARGET).semantics { contentDescription = description; state?.let { stateDescription = it } },
    ) {
        Text(
            text = glyph,
            color = toolInk(source, enabled, rest = if (dimmed || !enabled) Color.White.copy(alpha = DIM_ALPHA) else Color.White),
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
    val source = remember { MutableInteractionSource() }
    CatalogueTextButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = source,
        modifier = modifier.sizeIn(minWidth = MIN_TARGET, minHeight = MIN_TARGET).semantics { contentDescription = description },
    ) {
        TransportIcon(
            icon = icon,
            tint = toolInk(source, enabled, rest = if (enabled) Color.White else Color.White.copy(alpha = DIM_ALPHA)),
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
    state: String? = null,
) {
    val source = remember { MutableInteractionSource() }
    CatalogueTextButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = source,
        modifier = modifier.sizeIn(minWidth = MIN_TARGET, minHeight = MIN_TARGET).semantics { contentDescription = description; state?.let { stateDescription = it } },
    ) {
        Text(text = label, color = toolInk(source, enabled, rest = Color.White), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun TimeText(text: String) {
    Text(text = text, color = Color.White, style = MaterialTheme.typography.labelLarge)
}

/**
 * A tool's colour: [rest], or the stage's accent while a finger is on it or
 * a pointer over it, crossing over `--hover` (`.tool:hover`,
 * `player-card.css:79-82`; `.tsp:hover`, `playback.css:197-200`). The 8%
 * white fill under it is the indication's own tint ([OverVideoTone]). A
 * disabled tool keeps its colour, as `:disabled:hover` does
 * (`player-card.css:84`).
 */
@Composable
private fun toolInk(
    source: InteractionSource,
    enabled: Boolean,
    rest: Color,
): Color {
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val ink by animateColorAsState(if (enabled && (hovered || pressed)) StageAccent else rest, Motion.hover(), label = "tool")
    return ink
}

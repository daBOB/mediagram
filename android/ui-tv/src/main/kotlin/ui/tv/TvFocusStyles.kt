package ui.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceBorder
import androidx.tv.material3.ClickableSurfaceColors
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme

/** A focused card's border in [look]: the accent (mediagram), a white ring (Google TV), or none (Apple TV lifts instead). */
internal fun focusedCardBorderOf(look: TvLook, accent: Color, shape: Shape): Border =
    when (look.cardFocus) {
        TvCardFocus.ACCENT_BORDER -> Border(border = BorderStroke(TvFocus.BorderWidth, accent), shape = shape)
        TvCardFocus.RING -> Border(border = BorderStroke(TvFocus.BorderWidth, Color.White), shape = shape)
        TvCardFocus.LIFT -> Border.None
    }

/** Apple TV style's lift: a soft shadow under the grown card. Null — no glow — in every other style. */
internal fun focusedCardGlowOf(look: TvLook): Glow? =
    if (look.cardFocus == TvCardFocus.LIFT) Glow(elevationColor = Color.Black.copy(alpha = 0.6f), elevation = 16.dp) else null

/** The ring a focused plain surface (a swatch, a settings block, a dot) wears: the accent for mediagram, white otherwise. */
internal fun focusInkOf(look: TvLook, accent: Color): Color = if (look.cardFocus == TvCardFocus.ACCENT_BORDER) accent else Color.White

/** The fill a focused pill or button takes in Apple TV and Google TV styles, and the ink set on it. */
internal val FocusFill = Color(0xFFFFFFFF)
internal val FocusFillInk = Color(0xFF0A0A0B)

/** A pill's focused fill and ink in [look]: white and dark where the style fills on focus, else its own rest colours. */
internal fun pillFocusedColoursOf(
    look: TvLook,
    container: Color,
    content: Color,
): Pair<Color, Color> = if (look.pillFocusFills) FocusFill to FocusFillInk else container to content

/**
 * A pill's colours: [container] and [content] at rest, [pressed] while
 * pressed; focused, [pillFocusedColoursOf]. Read the content colour through
 * `LocalContentColor` inside the pill, never a fixed ink, so the dark ink on
 * a white fill reaches the label and the icon.
 */
@Composable
internal fun TvFocus.pillColors(
    container: Color,
    content: Color,
    pressed: Color = container,
): ClickableSurfaceColors {
    val (focusedContainer, focusedContent) = pillFocusedColoursOf(currentTvLook, container, content)
    return ClickableSurfaceDefaults.colors(
        containerColor = container,
        contentColor = content,
        focusedContainerColor = focusedContainer,
        focusedContentColor = focusedContent,
        pressedContainerColor = if (currentTvLook.pillFocusFills) focusedContainer else pressed,
        pressedContentColor = focusedContent,
    )
}

/** A pill's border: mediagram's ring on focus; none where the fill says focus instead. */
@Composable
internal fun TvFocus.pillBorder(): ClickableSurfaceBorder =
    if (currentTvLook.pillFocusFills) ClickableSurfaceDefaults.border() else surfaceBorder(shape = PillShape)

/** A line pill's border: [rest] at rest; focused, mediagram's accent ring, or none where the fill says focus instead. */
@Composable
internal fun TvFocus.linePillBorder(rest: Border): ClickableSurfaceBorder =
    ClickableSurfaceDefaults.border(
        border = rest,
        focusedBorder =
            if (currentTvLook.pillFocusFills) Border.None else Border(BorderStroke(BorderWidth, MaterialTheme.colorScheme.primary), shape = PillShape),
    )

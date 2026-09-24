package ui.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardGlow
import androidx.tv.material3.CardScale
import androidx.tv.material3.ClickableSurfaceBorder
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceGlow
import androidx.tv.material3.ClickableSurfaceScale
import designsystem.Palette

/**
 * One focus treatment for every TV screen, so a card or a text row reads
 * "this is what the remote is on" the same way wherever it appears instead
 * of each screen inventing its own emphasis.
 *
 * A television is worked from a couch several metres away, where a phone's
 * subtle tap states would be invisible. Focus has to read across a room:
 * the focused element grows ([Scale], enough to separate it from its
 * unfocused neighbours) and gains the catalogue's one accent as a border.
 * Imprint red already means "the title this viewer is in the middle of" on
 * the phone; TV reuses it for "the row the remote is currently on", so the
 * accent keeps one meaning across both surfaces instead of TV picking a
 * second colour for the same idea.
 *
 * No glow, anywhere. A blurred highlight reads against a bright wallpaper
 * carousel; on this catalogue's ink ground it only softens into another
 * warm smear next to the one accent this app allows itself, rather than
 * reading as a second cue. A text-only row — a menu line, a toggle — has
 * no card shape to put a border on at all, so it carries the same colour
 * plus an underline instead of a frame.
 */
object TvFocus {
    /** How far a focused element grows. Legible from the couch, not just the palm. */
    const val Scale = 1.08f

    /** Width of the accent border a focused card or surface gains. */
    val BorderWidth: Dp = 3.dp

    @Composable
    fun cardScale(): CardScale = CardDefaults.scale(focusedScale = Scale, pressedScale = Scale)

    @Composable
    fun cardBorder(shape: Shape = RectangleShape): CardBorder =
        CardDefaults.border(
            focusedBorder = Border(border = BorderStroke(BorderWidth, Palette.Imprint), shape = shape),
        )

    /** Explicit, not just the tv-material default: this catalogue never glows. */
    @Composable
    fun cardGlow(): CardGlow = CardDefaults.glow()

    @Composable
    fun surfaceScale(): ClickableSurfaceScale =
        ClickableSurfaceDefaults.scale(focusedScale = Scale, pressedScale = Scale)

    @Composable
    fun surfaceBorder(shape: Shape = RectangleShape): ClickableSurfaceBorder =
        ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(BorderWidth, Palette.Imprint), shape = shape),
        )

    @Composable
    fun surfaceGlow(): ClickableSurfaceGlow = ClickableSurfaceDefaults.glow()

    /**
     * A text-only row's focus state: the catalogue's accent colour, plus an
     * underline standing in for the border a card would have worn instead.
     */
    fun textStyle(base: TextStyle, focused: Boolean): TextStyle =
        if (focused) {
            base.copy(color = Palette.Imprint, textDecoration = TextDecoration.Underline)
        } else {
            base
        }
}

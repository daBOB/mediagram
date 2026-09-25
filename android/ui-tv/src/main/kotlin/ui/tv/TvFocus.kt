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
import androidx.tv.material3.CardShape
import androidx.tv.material3.ClickableSurfaceBorder
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceGlow
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.ClickableSurfaceShape
import androidx.tv.material3.MaterialTheme
import designsystem.Palette

/**
 * One focus treatment for every TV screen, so a card or a text row reads
 * "this is what the remote is on" the same way wherever it appears instead
 * of each screen inventing its own emphasis. A call site takes its shape,
 * scale, border and glow from here together — never the border alone — so
 * the frame a focused card grows a border in is always the frame the card
 * itself is already drawn in.
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
 * Square corners, not tv-material's own rounded default: the web player —
 * this catalogue's reference — draws every plate with square corners and
 * hairline rules, and a television plate is the same catalogue, not a
 * second design. [Shape] is the one constant the container and its focus
 * border are both cut from, so there is nowhere for a call site to pass a
 * shape to the card and forget to pass the same one to the border.
 *
 * No glow, anywhere, on a card or on a plain surface. A blurred highlight
 * reads against a bright wallpaper carousel; on this catalogue's ink
 * ground it only softens into another warm smear next to the one accent
 * this app allows itself, rather than reading as a second cue. A text-only
 * row — a menu line, a toggle — has no card shape to put a border on at
 * all, so it carries that same accent colour plus an underline instead of
 * a frame.
 */
object TvFocus {
    /** How far a focused element grows. Legible from the couch, not just the palm. */
    const val Scale = 1.08f

    /** Width of the accent border a focused card or surface gains. */
    val BorderWidth: Dp = 3.dp

    /**
     * Every TV container's corner, focused or not: square, like the web
     * player's plates. Not exposed as a per-call parameter — a shape a
     * caller could vary here is a shape that could drift from the border
     * built on top of it, which is the bug this constant exists to close off.
     */
    private val Shape: Shape = RectangleShape

    @Composable
    fun cardShape(): CardShape = CardDefaults.shape(shape = Shape, focusedShape = Shape, pressedShape = Shape)

    @Composable
    fun cardScale(): CardScale = CardDefaults.scale(focusedScale = Scale, pressedScale = Scale)

    /**
     * `CardBorder` has no disabled slot to leave unset — only `border`,
     * `focusedBorder` and `pressedBorder` — and tv-material defaults the
     * two left here to values already cut from [Shape]: `border` falls
     * back to `Border.None`, itself a `RectangleShape`, and `pressedBorder`
     * falls back to whatever `focusedBorder` resolved to, which is the
     * square one passed below. Nothing here can default to a rounded shape.
     */
    @Composable
    fun cardBorder(): CardBorder =
        CardDefaults.border(
            focusedBorder = Border(border = BorderStroke(BorderWidth, Palette.Imprint), shape = Shape),
        )

    /** Explicit, not just the tv-material default: this catalogue never glows. */
    @Composable
    fun cardGlow(): CardGlow = CardDefaults.glow()

    @Composable
    fun surfaceShape(): ClickableSurfaceShape =
        ClickableSurfaceDefaults.shape(
            shape = Shape,
            focusedShape = Shape,
            pressedShape = Shape,
            disabledShape = Shape,
            focusedDisabledShape = Shape,
        )

    @Composable
    fun surfaceScale(): ClickableSurfaceScale =
        ClickableSurfaceDefaults.scale(focusedScale = Scale, pressedScale = Scale)

    /**
     * `ClickableSurfaceBorder` carries two states `CardBorder` does not:
     * `disabledBorder`, which falls back to `border` (`Border.None`, a
     * `RectangleShape` — already square), and `focusedDisabledBorder`,
     * which does not chain from anything passed here and instead falls
     * back to tv-material's own default — a rounded `ShapeDefaults.Small`
     * corner nothing on this screen ever draws intentionally. Passed
     * explicitly here, on [Shape], so a surface that is ever both focused
     * and disabled still reads as this catalogue's square plate rather
     * than tv-material's own rounded one.
     */
    @Composable
    fun surfaceBorder(): ClickableSurfaceBorder =
        ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(BorderWidth, Palette.Imprint), shape = Shape),
            focusedDisabledBorder =
                Border(border = BorderStroke(BorderWidth, MaterialTheme.colorScheme.border), shape = Shape),
        )

    /** Explicit for the same reason as [cardGlow]: no glow on a plain surface either. */
    @Composable
    fun surfaceGlow(): ClickableSurfaceGlow = ClickableSurfaceDefaults.glow()

    /**
     * The one border a plain field or an always-on accent draws by hand,
     * since neither is a `ClickableSurface` — a text field takes no
     * `onClick`, and a loading spinner is not a focus state at all —
     * so neither can take [surfaceBorder]'s stateful pair. Cut from the
     * same [BorderWidth] and [Shape] as every other border here, so a field
     * or a spinner reads as this catalogue's accent rather than a value
     * invented locally.
     */
    @Composable
    fun fieldBorder(focused: Boolean): Border =
        Border(
            border = BorderStroke(BorderWidth, if (focused) Palette.Imprint else MaterialTheme.colorScheme.border),
            shape = Shape,
        )

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

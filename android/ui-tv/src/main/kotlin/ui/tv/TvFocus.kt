package ui.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
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
import designsystem.LocalTvStyle
import designsystem.Radius
import designsystem.TvStyle

/**
 * One focus treatment for every TV screen, so a card or a text row reads
 * "this is what the remote is on" the same way wherever it appears. A call
 * site takes its shape, scale, border and glow from here together — never
 * the border alone — so a focused card's border is cut from the frame the
 * card is drawn in. Each value reads the style's [TvLook].
 *
 * A television is worked from a couch several metres away, so focus has to
 * read across a room: the focused element grows and gains a cue. In
 * mediagram the cue is the catalogue's one accent as a border — imprint red
 * already means "the title this viewer is in the middle of" on the phone,
 * so it keeps one meaning — on square corners, as the web player draws its
 * plates. Apple TV style lifts the card on a soft shadow and Google TV
 * style rings it white; both round the corner ([TvLook.cardShape]).
 *
 * No glow otherwise: on this catalogue's ink ground a blurred highlight
 * only softens into a smear beside the accent. A text-only row — a menu
 * line, a toggle — has no frame to border, so it takes the accent plus an
 * underline instead.
 */
object TvFocus {
    /** How far a focused element grows. Legible from the couch, not just the palm. */
    const val Scale = 1.08f

    /**
     * The ink fill, as a share of the page's ink, that marks where the viewer
     * is within a list: a settings row that is the open section, and a
     * settings block the remote is resting on.
     */
    const val ChosenFillAlpha = 0.10f

    /** Width of the accent border a focused card or surface gains. */
    val BorderWidth: Dp = 3.dp

    /**
     * Every TV container's corner in the current style. Not a per-call
     * parameter: a shape a caller could vary here could drift from the
     * border built on top of it.
     */
    private val Shape: Shape
        @Composable
        @ReadOnlyComposable
        get() = currentTvLook.cardShape

    /** A department pill, a cover button — fully round, matching the web's own pill-shaped controls. */
    val PillShape: Shape = CircleShape

    /** A rail row — [designsystem.Radius.control], the same corner Settings' own index row already draws. */
    val ControlShape: Shape = RoundedCornerShape(Radius.control)

    /** Home's own feature card, at [Radius.card] like the phone's. */
    private val FeatureCardShape: Shape = RoundedCornerShape(Radius.card)

    /** A feature card's or an art tile's corner: [FeatureCardShape] in mediagram, the style's card corner otherwise. */
    val featureCardShape: Shape
        @Composable
        @ReadOnlyComposable
        get() = if (LocalTvStyle.current == TvStyle.MEDIAGRAM) FeatureCardShape else currentTvLook.cardShape

    @Composable
    fun cardShape(shape: Shape = Shape): CardShape = CardDefaults.shape(shape = shape, focusedShape = shape, pressedShape = shape)

    @Composable
    fun cardScale(): CardScale = CardDefaults.scale(focusedScale = currentTvLook.focusScale, pressedScale = currentTvLook.focusScale)

    /**
     * Only `focusedBorder` is set: `border` falls back to `Border.None` and
     * `pressedBorder` to the focused one, so nothing falls back to
     * tv-material's own corner.
     */
    @Composable
    fun cardBorder(shape: Shape = Shape): CardBorder =
        CardDefaults.border(
            focusedBorder = focusedCardBorderOf(currentTvLook, MaterialTheme.colorScheme.primary, shape),
        )

    /** Explicit, not just the tv-material default: only Apple TV style's lift glows. */
    @Composable
    fun cardGlow(): CardGlow =
        focusedCardGlowOf(currentTvLook)?.let { CardDefaults.glow(focusedGlow = it, pressedGlow = it) } ?: CardDefaults.glow()

    @Composable
    fun surfaceShape(shape: Shape = Shape): ClickableSurfaceShape =
        ClickableSurfaceDefaults.shape(
            shape = shape,
            focusedShape = shape,
            pressedShape = shape,
            disabledShape = shape,
            focusedDisabledShape = shape,
        )

    @Composable
    fun surfaceScale(): ClickableSurfaceScale =
        ClickableSurfaceDefaults.scale(focusedScale = currentTvLook.focusScale, pressedScale = currentTvLook.focusScale)

    /**
     * `focusedDisabledBorder` chains from nothing passed here and would fall
     * back to tv-material's rounded `ShapeDefaults.Small`, so it is cut from
     * [shape] too: a surface both focused and disabled keeps the style's
     * corner — square in mediagram, [TvLook.cardShape] otherwise.
     *
     * [selected] rings an unfocused surface — a swatch that is the current
     * choice, which would otherwise only say so to a screen reader — in
     * [selectedInkOf]'s colour, never the one focus rings in.
     */
    @Composable
    fun surfaceBorder(selected: Boolean = false, shape: Shape = Shape): ClickableSurfaceBorder =
        ClickableSurfaceDefaults.border(
            border =
                if (selected) {
                    val ink = selectedInkOf(currentTvLook, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onSurface)
                    Border(border = BorderStroke(BorderWidth, ink), shape = shape)
                } else {
                    Border.None
                },
            focusedBorder =
                Border(border = BorderStroke(BorderWidth, focusInkOf(currentTvLook, MaterialTheme.colorScheme.primary)), shape = shape),
            focusedDisabledBorder =
                Border(border = BorderStroke(BorderWidth, MaterialTheme.colorScheme.border), shape = shape),
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
    fun fieldBorder(focused: Boolean, shape: Shape = Shape): Border =
        Border(
            border = BorderStroke(BorderWidth, if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.border),
            shape = shape,
        )

    /**
     * A text-only row's focus state: the catalogue's accent colour, plus an
     * underline standing in for the border a card would have worn instead.
     */
    @Composable
    fun textStyle(base: TextStyle, focused: Boolean): TextStyle =
        if (focused) {
            base.copy(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)
        } else {
            base
        }
}

/**
 * [wanted] when a caller names a requester for this focus target, else one
 * of the target's own. A focus target's modifier chain must carry exactly
 * one `focusRequester` on every recomposition: a requester appearing or
 * disappearing as a row's arrival target moves between cards makes Compose
 * treat the node as new and drop the focus it held, and the remote falls
 * back to the bar. Call it unconditionally, in the same place as the target.
 */
@Composable
internal fun rememberStableRequester(wanted: FocusRequester?): FocusRequester {
    val own = remember { FocusRequester() }
    return wanted ?: own
}

package ui.common.chrome

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource

/*
 * The departments bar's frosted glass, shared by the tablet's bar and the
 * television's: the web's masthead (`shell.css:84-87`) blurs whatever scrolls
 * or bleeds under it, and both surfaces do the same with the same numbers.
 */

/** The bar's own colour over the cover, translucent black with light type — the web's `[data-cover]` opening state (`shell.css:233`). */
val OverCoverBg = Color(0x590A0A0B)

/** The scrolled bar's paper tint over the blur — the web's `color-mix(in srgb, var(--paper) 72%, transparent)` (`shell.css:84`). */
const val FrostedPaperAlpha = 0.72f

/** The hairline under the scrolled bar at full strength — the web's `--rule-soft` on the dark theme (`theme.css:86`), `border-bottom` (`shell.css:87`). */
private const val HairlineAlpha = 0.08f

/**
 * The bar's own background, blended between [OverCoverBg] and [solid]. Over a
 * [frosted] backdrop blur the scrolled end is [solid] at the web's 72% (the
 * blur is what keeps that readable); without one it reaches [solid] exactly
 * (not just close) once [blend] is 1, since nothing would blur whatever a deep
 * scroll shows through it.
 */
fun barBackground(
    solid: Color,
    blend: Float,
    frosted: Boolean,
): Color = lerp(OverCoverBg, if (frosted) solid.copy(alpha = FrostedPaperAlpha) else solid, blend)

/** `RenderEffect` blurs arrive with API 31; Haze draws only a scrim below it, which is the look the bar had before, so below it nothing here is applied. */
fun frostAvailable(sdk: Int = Build.VERSION.SDK_INT): Boolean = sdk >= 31

/** The state a frosted bar and the content behind it share, or `null` where there is no blur to share it for. */
@Composable
fun rememberFrost(): HazeState? = remember { if (frostAvailable()) HazeState() else null }

/**
 * The [HazeState] a node shares, as the bar blurring or the content blurred,
 * so a test can read that the bar is frosted, and over what, rather than its
 * pixels.
 */
val FrostKey = SemanticsPropertyKey<HazeState>("Frost")

/** Marks this content as what the bar blurs, and reports [frost] as [FrostKey]; no-op without [frost]. */
fun Modifier.frostSource(frost: HazeState?): Modifier = if (frost == null) this else semantics { this[FrostKey] = frost }.hazeSource(frost)

/**
 * The web's `blur(18px) saturate(1.15)` with a blur of [radius]. CSS `blur(18px)` is a standard
 * deviation of 18 CSS px and Haze's radius is the same sigma. `saturate(1.15)` is a saturation
 * matrix; noise is off because the web draws none. Held in a `val` by each caller: a new style
 * every recomposition would hand Haze a changed effect on every frame of a scroll.
 */
fun frostBlurStyle(radius: Dp): HazeBlurStyle =
    HazeBlurStyle {
        blurRadius(radius)
        noiseFactor(0f)
        colorEffects(listOf(HazeColorEffect.colorFilter(ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.15f) }))))
    }

// A CSS px is one dp on the phone and tablet, so the web's 18px carries over unscaled.
private val FrostStyle = frostBlurStyle(18.dp)

/** Blurs what [frost] sources behind this node in [style], and reports [frost] as [FrostKey]; no-op without [frost]. */
fun Modifier.frostedBackdrop(frost: HazeState?, style: HazeBlurStyle = FrostStyle): Modifier =
    if (frost == null) this else semantics { this[FrostKey] = frost }.hazeBlur(HazeInput.Sources(frost), style)

/** The 1dp bottom border, in [ink] at [HairlineAlpha] scaled by [blend]; no-op without [frost]. */
fun Modifier.frostHairline(ink: Color, blend: Float, frost: HazeState?): Modifier =
    if (frost == null) {
        this
    } else {
        // Transparent over the cover, as the web's `[data-cover] .masthead` is (`shell.css:236`).
        drawBehind { drawLine(ink.copy(alpha = HairlineAlpha * blend), start = Offset(0f, size.height), end = Offset(size.width, size.height), strokeWidth = 1.dp.toPx()) }
    }

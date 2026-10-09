package ui.chrome

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource

/** The scrolled bar's paper tint over the blur — the web's `color-mix(in srgb, var(--paper) 72%, transparent)` (`shell.css:83`). */
internal const val FrostedPaperAlpha = 0.72f

/** The hairline under the scrolled bar at full strength — the web's `--rule-soft` on the dark theme (`theme.css:86`), `border-bottom` (`shell.css:86`). */
private const val HairlineAlpha = 0.08f

/** `RenderEffect` blurs arrive with API 31; Haze draws only a scrim below it, which is the look this bar had before, so below it nothing here is applied. */
internal fun frostAvailable(sdk: Int = Build.VERSION.SDK_INT): Boolean = sdk >= 31

/** The state a frosted bar and the content behind it share, or `null` where there is no blur to share it for. */
@Composable
internal fun rememberFrost(): HazeState? = remember { if (frostAvailable()) HazeState() else null }

/** Marks this content as what the bar blurs; no-op without [frost]. */
internal fun Modifier.frostSource(frost: HazeState?): Modifier = if (frost == null) this else hazeSource(frost)

// CSS `blur(18px)` is a standard deviation of 18 CSS px; Haze's radius is the same sigma and a CSS px is one dp, so 18.dp carries over unscaled. `saturate(1.15)` is a saturation matrix; noise is off because the web draws none.
private val FrostStyle =
    HazeBlurStyle {
        blurRadius(18.dp)
        noiseFactor(0f)
        colorEffects(listOf(HazeColorEffect.colorFilter(ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.15f) }))))
    }

/** Blurs what [frost] sources behind this node; no-op without [frost]. */
internal fun Modifier.frostedBackdrop(frost: HazeState?): Modifier = if (frost == null) this else hazeBlur(HazeInput.Sources(frost), FrostStyle)

/** The 1dp bottom border, in [ink] at [HairlineAlpha] scaled by [blend]; no-op without [frost]. */
internal fun Modifier.frostHairline(ink: Color, blend: Float, frost: HazeState?): Modifier =
    if (frost == null) {
        this
    } else {
        // Transparent over the cover, as the web's `[data-cover] .masthead` is (`shell.css:236`).
        drawBehind { drawLine(ink.copy(alpha = HairlineAlpha * blend), start = Offset(0f, size.height), end = Offset(size.width, size.height), strokeWidth = 1.dp.toPx()) }
    }

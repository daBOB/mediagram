package designsystem

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * The web player's motion tokens (`web/public/styles/theme.css:120-122`),
 * so a fade here runs on the same curve and clock as the same fade there.
 * The two CSS keyword curves are the ones the web's player controls and
 * Featured reel use instead of a token; naming them keeps those two places
 * from guessing at `ease`.
 */
object Motion {
    /** `--ease`: standard motion, a settling spring. */
    val Ease = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

    /** `--ease-out`: the cover's slow zoom. */
    val EaseOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    /** CSS `ease`: the player's controls fading, the Featured reel's turn. */
    val CssEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

    /** CSS `ease-out`: the Featured poster's drift and its copy rising. */
    val CssEaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

    /** `--hover`: a change of colour, border or background, never of size. */
    const val HOVER_MS = 160

    fun <T> hover(): TweenSpec<T> = tween(HOVER_MS, easing = Ease)
}

/**
 * Whether the system asks for no animation, which is the web's
 * `prefers-reduced-motion`: Android says so by turning animator durations
 * off. Read once, not observed. Compose's own tweens already follow the
 * setting live, and this only steers the few places whose end state is not
 * their rest (a drift, a timer), for a setting viewers rarely flip.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/**
 * [rememberReducedMotion], read once by the theme so the motion nodes can
 * reach it with `currentValueOf` and tests can set it outright.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

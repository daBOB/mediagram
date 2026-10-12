package designsystem

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Every control's corner, at the web's `--radius` (6dp) — inputs, buttons,
 * panels and dialogs. `extraLarge` (sheets, full-screen dialogs) is left at
 * M3's own default rather than swept into the same 6dp: nothing on this
 * catalogue draws one yet, and a size guessed without a screen to check it
 * against is a follow-up, not a decision.
 */
private val CatalogueShapes =
    Shapes(
        extraSmall = RoundedCornerShape(Radius.control),
        small = RoundedCornerShape(Radius.control),
        medium = RoundedCornerShape(Radius.control),
        large = RoundedCornerShape(Radius.control),
    )

@Composable
fun MediagramTheme(
    appearance: Appearance = Appearance(),
    content: @Composable () -> Unit,
) {
    val dark =
        when (appearance.theme) {
            ThemeChoice.DARK -> true
            ThemeChoice.LIGHT -> false
            ThemeChoice.AUTO -> isSystemInDarkTheme()
        }
    val accentColor = appearance.accent.resolve(dark)
    // The system bars follow the resolved theme, not the system's own:
    // MainActivity's `enableEdgeToEdge` only had a pre-Compose guess to go
    // on, and a viewer who picked Light or Dark over a differently-set
    // system would otherwise keep icons drawn for the theme they did not
    // choose. Runs on every change to `dark`, so flipping the setting live
    // corrects the bars at once.
    val view = LocalView.current
    val context = LocalContext.current
    SideEffect {
        val window = context.findActivity()?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }
    val scheme = catalogueColorScheme(dark, accentColor)
    CompositionLocalProvider(
        LocalCatalogueTones provides if (dark) DarkTones else LightTones,
        LocalBackdrop provides appearance.backdrop,
        LocalReducedMotion provides rememberReducedMotion(),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = CatalogueTypography,
            shapes = CatalogueShapes,
        ) {
            // Inside MaterialTheme, which provides ripple as LocalIndication: the
            // web answers a press with a quiet tint and a 0.98 snap, never a ripple
            // (theme.css:164-167). Material's own widgets hard-code their ripple,
            // so turning its configuration off silences them; ui.chrome's
            // Catalogue*Button wrappers draw this indication for them instead.
            CompositionLocalProvider(
                LocalIndication provides catalogueIndication(),
                LocalRippleConfiguration provides null,
                LocalIndicationTone provides IndicationTone(ink = scheme.onSurface, ring = accentColor),
                content = content,
            )
        }
    }
}

/** Same shape as `ui.common.player.findActivity` in ui-common — duplicated rather than depended on, since neither module depends on the other. */
private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

package designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color

/**
 * Dark by default, on a phone, a tablet, or a television — a media library
 * is looked at in the dark — but Settings › Appearance can now ask for
 * Light instead, or Auto to follow the system's own theme; [appearance]
 * carries that choice, [MediagramTheme] resolves it.
 *
 * Not Material's baseline dark or light, which are a violet-tinted neutral
 * nobody here chose. The roles below are the catalogue's own palette, so a
 * component that reaches for `colorScheme.surface` knowing nothing about
 * this app still lands on the page.
 *
 * Dynamic colour is deliberately not offered. Material You would derive
 * the scheme from the viewer's wallpaper, and this surface is a printed
 * object whose one accent means something a wallpaper cannot be allowed
 * to decide — Settings' seven [Accent] choices are the closest this
 * catalogue comes to letting a viewer repaint it.
 */
internal fun catalogueColorScheme(
    dark: Boolean,
    accent: Color,
): ColorScheme =
    if (dark) {
        darkColorScheme(
            background = Palette.Ground,
            onBackground = Palette.Text,
            surface = Palette.Page,
            onSurface = Palette.Text,
            surfaceVariant = Palette.Sunk,
            onSurfaceVariant = Palette.Figures,
            surfaceContainerLowest = Palette.Ground,
            surfaceContainerLow = Palette.Ground,
            surfaceContainer = Palette.Page,
            surfaceContainerHigh = Palette.Sunk,
            surfaceContainerHighest = Palette.Sunk,
            primary = accent,
            onPrimary = Palette.Ground,
            secondary = Palette.Figures,
            onSecondary = Palette.Ground,
            tertiary = Palette.Sage,
            onTertiary = Palette.Ground,
            outline = Palette.RuleStrong,
            outlineVariant = Palette.Rule,
            // The container roles, and every role a component might reach for
            // without this app ever naming it. `darkColorScheme` fills anything
            // left out with Material's baseline, which is violet, so an unset role
            // is not a neutral default — it is the one colour this scheme exists to
            // get rid of, waiting for a component to ask. The progress line asked:
            // `LinearProgressIndicator` draws its track from `secondaryContainer`,
            // and drew it in lavender over the catalogue for as long as that role
            // went unnamed.
            primaryContainer = Palette.Sunk,
            onPrimaryContainer = accent,
            secondaryContainer = Palette.Sunk,
            onSecondaryContainer = Palette.Text,
            tertiaryContainer = Palette.Sunk,
            onTertiaryContainer = Palette.Sage,
            errorContainer = Palette.Sunk,
            onErrorContainer = Palette.Ochre,
            surfaceBright = Palette.Sunk,
            surfaceDim = Palette.Ground,
            // Elevation is tinted with this, and nothing here is elevated; naming
            // it keeps the tint the page's own colour if anything ever is.
            surfaceTint = accent,
            inverseSurface = Palette.Text,
            inverseOnSurface = Palette.Ground,
            inversePrimary = accent,
            scrim = Palette.Ground,
            // A refresh that failed is the catalogue warning, not an alarm: the
            // library on screen is still every bit of the one that was there
            // before it was tried. Ochre is what the web player says that in, and
            // a signal red here would claim something worse than what happened.
            error = Palette.Ochre,
            onError = Palette.Ground,
        )
    } else {
        lightColorScheme(
            background = Palette.LightGround,
            onBackground = Palette.LightText,
            surface = Palette.LightPage,
            onSurface = Palette.LightText,
            surfaceVariant = Palette.LightSunk,
            onSurfaceVariant = Palette.LightFigures,
            surfaceContainerLowest = Palette.LightGround,
            surfaceContainerLow = Palette.LightGround,
            surfaceContainer = Palette.LightPage,
            surfaceContainerHigh = Palette.LightSunk,
            surfaceContainerHighest = Palette.LightSunk,
            primary = accent,
            onPrimary = Palette.LightGround,
            secondary = Palette.LightFigures,
            onSecondary = Palette.LightGround,
            tertiary = Palette.LightSage,
            onTertiary = Palette.LightGround,
            outline = Palette.LightRuleStrong,
            outlineVariant = Palette.LightRule,
            primaryContainer = Palette.LightSunk,
            onPrimaryContainer = accent,
            secondaryContainer = Palette.LightSunk,
            onSecondaryContainer = Palette.LightText,
            tertiaryContainer = Palette.LightSunk,
            onTertiaryContainer = Palette.LightSage,
            errorContainer = Palette.LightSunk,
            onErrorContainer = Palette.LightOchre,
            surfaceBright = Palette.LightPage,
            surfaceDim = Palette.LightSunk,
            surfaceTint = accent,
            inverseSurface = Palette.LightText,
            inverseOnSurface = Palette.LightGround,
            inversePrimary = accent,
            scrim = Palette.LightText,
            error = Palette.LightOchre,
            onError = Palette.LightGround,
        )
    }

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
    // The one place this module is allowed to write Palette.Imprint: every
    // reader below and across the television surface sees this composition's
    // resolved accent, not a value they each had to be handed separately.
    SideEffect { Palette.Imprint = accentColor }
    MaterialTheme(
        colorScheme = catalogueColorScheme(dark, accentColor),
        typography = CatalogueTypography,
        content = content,
    )
}

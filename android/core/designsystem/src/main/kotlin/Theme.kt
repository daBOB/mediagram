package designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Dark by default, on a phone, a tablet, or a television, regardless of
 * the system theme: a media library is looked at in the dark.
 *
 * Not Material's baseline dark, which is a violet-tinted neutral nobody
 * here chose, and which arrived only because `darkColorScheme()` was called
 * with no arguments. The roles below are the catalogue's own palette, so a
 * component that reaches for `colorScheme.surface` knowing nothing about
 * this app still lands on the page.
 *
 * Dynamic colour is deliberately not offered. Material You would derive the
 * scheme from the viewer's wallpaper, and this surface is a printed object
 * whose one accent means something; a wallpaper cannot be allowed to decide
 * what the catalogue warns in.
 */
internal val CatalogueColors =
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
        primary = Palette.Imprint,
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
        onPrimaryContainer = Palette.Imprint,
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
        surfaceTint = Palette.Imprint,
        inverseSurface = Palette.Text,
        inverseOnSurface = Palette.Ground,
        inversePrimary = Palette.Imprint,
        scrim = Palette.Ground,
        // A refresh that failed is the catalogue warning, not an alarm: the
        // library on screen is still every bit of the one that was there
        // before it was tried. Ochre is what the web player says that in, and
        // a signal red here would claim something worse than what happened.
        error = Palette.Ochre,
        onError = Palette.Ground,
    )

@Composable
fun MediagramTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CatalogueColors,
        typography = CatalogueTypography,
        content = content,
    )
}

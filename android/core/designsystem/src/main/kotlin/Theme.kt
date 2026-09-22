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
private val CatalogueColors = darkColorScheme(
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

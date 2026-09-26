package ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import designsystem.Accent
import designsystem.Palette
import designsystem.TvTypeScale

/**
 * Dark, always: a media library read from the couch is read in the dark,
 * regardless of what the set's own theme is set to, and a television is
 * watched in a dark room in a way a phone is not — Settings › Appearance's
 * theme choice (Dark/Light/Auto) is a phone-only question here, a
 * deliberate difference from the web recorded in phase 8 of the
 * editorial-departments parity plan. [accent] is the one part of
 * Appearance the television does answer, at its own dark value.
 *
 * tv-material brings its own `MaterialTheme` and `ColorScheme` type —
 * `:ui-tv` never has material3 on its compile classpath, so
 * `designsystem.MediagramTheme` cannot reach this surface at all, and this
 * is the adapter that stands in for it. The colours handed to
 * `darkColorScheme` below are [Palette]'s own values, not a second set
 * retyped for the television, so both theme adapters read one palette the
 * same way and a hex changed in one place changes on both surfaces.
 */
internal fun tvColorScheme(accent: Color): ColorScheme =
    darkColorScheme(
        primary = accent,
        onPrimary = Palette.Ground,
        primaryContainer = Palette.Sunk,
        onPrimaryContainer = accent,
        inversePrimary = accent,
        secondary = Palette.Figures,
        onSecondary = Palette.Ground,
        secondaryContainer = Palette.Sunk,
        onSecondaryContainer = Palette.Text,
        tertiary = Palette.Sage,
        onTertiary = Palette.Ground,
        tertiaryContainer = Palette.Sunk,
        onTertiaryContainer = Palette.Sage,
        background = Palette.Ground,
        onBackground = Palette.Text,
        surface = Palette.Page,
        onSurface = Palette.Text,
        surfaceVariant = Palette.Sunk,
        onSurfaceVariant = Palette.Figures,
        surfaceTint = accent,
        inverseSurface = Palette.Text,
        inverseOnSurface = Palette.Ground,
        error = Palette.Ochre,
        onError = Palette.Ground,
        errorContainer = Palette.Sunk,
        onErrorContainer = Palette.Ochre,
        border = Palette.RuleStrong,
        borderVariant = Palette.Rule,
        scrim = Palette.Ground,
    )

/**
 * [TvTypeScale] set into every title/headline and body slot of tv-material's
 * `Typography`, so a shelf name reads at the same size regardless of which
 * slot a screen reaches for, and so does a sentence or a runtime figure.
 * Display and label slots are left at tv-material's own baseline: nothing
 * on this surface asks for either yet, and inventing a size nobody needs is
 * a second scale to keep in step with [TvTypeScale] for no reader.
 */
private val TvTypography =
    Typography().copy(
        headlineLarge = TvTypeScale.title,
        headlineMedium = TvTypeScale.title,
        headlineSmall = TvTypeScale.title,
        titleLarge = TvTypeScale.title,
        titleMedium = TvTypeScale.title,
        titleSmall = TvTypeScale.title,
        bodyLarge = TvTypeScale.body,
        bodyMedium = TvTypeScale.body,
        bodySmall = TvTypeScale.body,
    )

/** The one theme every TV screen composes under; no screen builds its own. */
@Composable
fun TvTheme(
    accent: Accent = Accent.Default,
    content: @Composable () -> Unit,
) {
    val accentColor = accent.dark
    // The one place this module is allowed to write Palette.Imprint: see
    // designsystem.MediagramTheme's own SideEffect for why.
    SideEffect { Palette.Imprint = accentColor }
    MaterialTheme(
        colorScheme = tvColorScheme(accentColor),
        typography = TvTypography,
        content = content,
    )
}

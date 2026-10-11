package ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import designsystem.Accent
import designsystem.Backdrop
import designsystem.LocalBackdrop
import designsystem.LocalReducedMotion
import designsystem.LocalTvStyle
import designsystem.Overscan
import designsystem.Palette
import designsystem.TvStyle
import designsystem.TvType
import designsystem.inStyle
import designsystem.rememberReducedMotion

/**
 * Dark, always: a media library read from the couch is read in the dark,
 * regardless of what the set's own theme is set to, and a television is
 * watched in a dark room in a way a phone is not — Settings › Appearance's
 * theme choice (Dark/Light/Auto) is a phone-only question here, a
 * deliberate difference from the web recorded in
 * `docs/system-architecture.md`. [accent] is the one part of Appearance the
 * television does answer, at its own dark value.
 *
 * tv-material brings its own `MaterialTheme` and `ColorScheme` type —
 * `:ui-tv` never has material3 on its compile classpath, so
 * `designsystem.MediagramTheme` cannot reach this surface at all, and this
 * is the adapter that stands in for it. The colours handed to
 * `darkColorScheme` below are [Palette]'s own values for mediagram — not a
 * second set retyped for the television — and a style's [TvNeutrals]
 * otherwise, so both theme adapters read one palette the same way and a hex
 * changed in one place changes on both surfaces.
 */
internal fun tvColorScheme(accent: Color, neutrals: TvNeutrals = MediagramLook.neutrals): ColorScheme =
    darkColorScheme(
        primary = accent,
        onPrimary = neutrals.ground,
        primaryContainer = neutrals.sunk,
        onPrimaryContainer = accent,
        inversePrimary = accent,
        secondary = neutrals.figures,
        onSecondary = neutrals.ground,
        secondaryContainer = neutrals.sunk,
        onSecondaryContainer = neutrals.text,
        tertiary = Palette.Sage,
        onTertiary = neutrals.ground,
        tertiaryContainer = neutrals.sunk,
        onTertiaryContainer = Palette.Sage,
        background = neutrals.ground,
        onBackground = neutrals.text,
        surface = neutrals.page,
        onSurface = neutrals.text,
        surfaceVariant = neutrals.sunk,
        onSurfaceVariant = neutrals.figures,
        surfaceTint = accent,
        inverseSurface = neutrals.text,
        inverseOnSurface = neutrals.ground,
        error = Palette.Ochre,
        onError = neutrals.ground,
        errorContainer = neutrals.sunk,
        onErrorContainer = Palette.Ochre,
        border = neutrals.ruleStrong,
        borderVariant = neutrals.rule,
        scrim = neutrals.ground,
    )

/**
 * [TvType]'s roles in the tv-material slots they are named for, as
 * `designsystem`'s own `CatalogueTypography` sets them into M3's on the
 * phone — so a tv-material component that reaches for a slot of its own (a
 * `Button`'s label, a bare `Text`) draws the web's size, the same as every
 * screen here that names its role directly. Display and headline slots
 * above `headlineSmall` keep tv-material's baseline, as the phone's keep M3's.
 */
private fun tvTypography(style: TvStyle): Typography =
    Typography().copy(
        headlineSmall = TvType.Base.headlineSmall.inStyle(style),
        titleLarge = TvType.Base.titleLarge.inStyle(style),
        titleMedium = TvType.Base.titleMedium.inStyle(style),
        titleSmall = TvType.Base.titleSmall.inStyle(style),
        bodyLarge = TvType.Base.bodyLarge.inStyle(style),
        bodyMedium = TvType.Base.bodyMedium.inStyle(style),
        bodySmall = TvType.Base.bodySmall.inStyle(style),
        labelLarge = TvType.Base.labelLarge.inStyle(style),
        labelMedium = TvType.Base.labelMedium.inStyle(style),
        labelSmall = TvType.Base.labelSmall.inStyle(style),
    )

/**
 * The one theme every TV screen composes under; no screen builds its own.
 * Like [accent] and unlike theme, [backdrop] is not a phone-only question —
 * see [LocalBackdrop]'s own doc for why a `CompositionLocal` carries it to
 * every hero rather than each one reading the settings `StateFlow` for
 * itself. [style] picks the TV's look ([TvLook]) and is provided as
 * [LocalTvStyle] for the seams whose layout differs by style.
 */
@Composable
fun TvTheme(
    accent: Accent = Accent.Default,
    backdrop: Backdrop = Backdrop.Default,
    style: TvStyle = TvStyle.Default,
    content: @Composable () -> Unit,
) {
    val accentColor = accent.dark
    // The web's `prefers-reduced-motion` (`theme.css:180`), read once at the
    // root as the phone's theme reads it, so the film leader stands still for
    // a viewer who turned animations off.
    CompositionLocalProvider(
        LocalBackdrop provides backdrop,
        LocalReducedMotion provides rememberReducedMotion(),
        LocalTvStyle provides style,
    ) {
        MaterialTheme(
            colorScheme = tvColorScheme(accentColor, style.look.neutrals),
            typography = remember(style) { tvTypography(style) },
            content = content,
        )
    }
}

/**
 * The safe area a screen reaches for when it has nothing of its own to
 * lay out: padded by [Overscan] and centred, the way a message standing in
 * for a wall needs to be drawn. A real lazy wall or a full-bleed player does not use
 * this — see [TvShell] for why each of those owns a different safe area.
 */
@Composable
internal fun TvSafeArea(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

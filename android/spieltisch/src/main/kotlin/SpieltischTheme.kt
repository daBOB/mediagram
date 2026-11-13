package com.mediagram.spieltisch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import designsystem.Accent
import designsystem.Palette

/**
 * The media app's television look in its own style, mediagram with the default accent: [Palette]'s
 * values, not a second set. Spieltisch has no Settings, so it never takes another style or accent.
 */
@Composable
fun SpieltischTheme(content: @Composable () -> Unit) {
    val scheme =
        darkColorScheme(
            primary = Accent.Default.dark,
            background = Palette.Ground,
            onBackground = Palette.Text,
            surface = Palette.Page,
            onSurface = Palette.Text,
            surfaceVariant = Palette.Sunk,
            onSurfaceVariant = Palette.Figures,
        )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground) {
            Box(Modifier.fillMaxSize().background(scheme.background)) { content() }
        }
    }
}

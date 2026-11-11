package com.mediagram.spieltisch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import designsystem.Accent
import designsystem.Palette
import designsystem.tvDp

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

// The film shelves' values in the media app (ui-tv's TvFocus, TvPageSpacing and TvPlate), so the two shelves match.

/** Text over a backdrop: the cover's paper white. */
internal val OnImage = Color(0xFFF6F2EA)

/** A poster's own proportions. */
internal const val PosterAspectRatio = 2f / 3f

/** mediagram's focus: the card grows by this and gains the accent as a square border [FocusBorder] wide. */
internal const val FocusScale = 1.08f
internal val FocusBorder = 3.dp

/** `.grid.plates.strip`'s gap and its column floor, `8.5rem` (`catalog.css:35-36`). */
internal val StripGap = 14.tvDp
private val StripPlateFloor = 136.tvDp

/** A poster's width in a row [rowWidth] wide: an eighth of the row less its gaps, or as many whole posters as fit at the floor. */
internal fun stripPlateWidth(rowWidth: Dp): Dp {
    if (!rowWidth.isFinite) return StripPlateFloor
    val fit = ((rowWidth + StripGap) / (StripPlateFloor + StripGap)).toInt().coerceIn(1, 8)
    return (rowWidth - StripGap * (fit - 1)) / fit
}

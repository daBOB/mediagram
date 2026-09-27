package designsystem

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/** WCAG's own floor for body-sized text — the Measured Colour Rule every text-carrying value in [Palette] is held to. */
internal const val MINIMUM_CONTRAST = 4.5

private fun channelLuminance(component: Float): Double {
    val value = component.toDouble()
    return if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
}

/** Relative luminance, WCAG 2.x's own formula — shared so [AccentContrastTest] and [PaletteContrastTest] measure the same way. */
internal fun luminance(color: Color): Double =
    0.2126 * channelLuminance(color.red) + 0.7152 * channelLuminance(color.green) + 0.0722 * channelLuminance(color.blue)

internal fun contrast(
    a: Color,
    b: Color,
): Double {
    val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
    return (hi + 0.05) / (lo + 0.05)
}

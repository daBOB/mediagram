package designsystem

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Port of `web/test/appearance-contrast.test.ts`: every [Accent] reads at
 * least 4.5:1 against its own theme's paper. Checked against Android's own
 * paper values ([Palette.Ground], [Palette.LightGround]) rather than the
 * web's, since [Palette.Ground] is its own warmer near-black rather than
 * the web's literal `#0d0d0e` — see [Palette]'s own doc.
 */
class AccentContrastTest {
    @Test
    fun everyAccentReadsAtLeast4Point5ToOneOnItsThemesPaper() {
        for (accent in Accent.entries) {
            val darkRatio = contrast(accent.dark, Palette.Ground)
            assertTrue(darkRatio >= MINIMUM_RATIO, "${accent.name} dark reads $darkRatio on Palette.Ground, need $MINIMUM_RATIO")

            val lightRatio = contrast(accent.light, Palette.LightGround)
            assertTrue(
                lightRatio >= MINIMUM_RATIO,
                "${accent.name} light reads $lightRatio on Palette.LightGround, need $MINIMUM_RATIO",
            )
        }
    }
}

private const val MINIMUM_RATIO = 4.5

private fun channelLuminance(component: Float): Double {
    val value = component.toDouble()
    return if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
}

private fun luminance(color: Color): Double =
    0.2126 * channelLuminance(color.red) + 0.7152 * channelLuminance(color.green) + 0.0722 * channelLuminance(color.blue)

private fun contrast(
    a: Color,
    b: Color,
): Double {
    val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
    return (hi + 0.05) / (lo + 0.05)
}

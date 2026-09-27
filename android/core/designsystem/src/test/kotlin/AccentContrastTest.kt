package designsystem

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Port of `web/test/appearance-contrast.test.ts`: every [Accent] reads at
 * least 4.5:1 against its own theme's paper. Checked against Android's own
 * paper values ([Palette.Ground], [Palette.LightGround]) rather than a
 * literal from the web, though the two now agree exactly — see [Palette]'s
 * own doc — because a hex retyped here is a hex that can drift unnoticed.
 */
class AccentContrastTest {
    @Test
    fun everyAccentReadsAtLeast4Point5ToOneOnItsThemesPaper() {
        for (accent in Accent.entries) {
            val darkRatio = contrast(accent.dark, Palette.Ground)
            assertTrue(darkRatio >= MINIMUM_CONTRAST, "${accent.name} dark reads $darkRatio on Palette.Ground, need $MINIMUM_CONTRAST")

            val lightRatio = contrast(accent.light, Palette.LightGround)
            assertTrue(
                lightRatio >= MINIMUM_CONTRAST,
                "${accent.name} light reads $lightRatio on Palette.LightGround, need $MINIMUM_CONTRAST",
            )
        }
    }
}

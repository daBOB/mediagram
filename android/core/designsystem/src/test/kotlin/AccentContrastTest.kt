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

    /**
     * The page is not the only place an accent has to read: `catalogueColorScheme`
     * sets `onPrimaryContainer = accent` over `primaryContainer`, which is
     * [Palette.Sunk] in the dark scheme and [Palette.LightSunk] in the light
     * one — a chip or a button's own container, not the ground behind it.
     * [Palette.LightPage] is checked too: the light scheme's `surface`, where
     * a light-theme control carrying the accent most often sits.
     */
    @Test
    fun everyAccentReadsAtLeast4Point5ToOneOnTheContainersItSitsOn() {
        for (accent in Accent.entries) {
            for ((color, ground, name) in listOf(
                Triple(accent.dark, Palette.Sunk, "dark on Palette.Sunk"),
                Triple(accent.light, Palette.LightSunk, "light on Palette.LightSunk"),
                Triple(accent.light, Palette.LightPage, "light on Palette.LightPage"),
            )) {
                val ratio = contrast(color, ground)
                assertTrue(ratio >= MINIMUM_CONTRAST, "${accent.name} $name reads $ratio, need $MINIMUM_CONTRAST")
            }
        }
    }
}

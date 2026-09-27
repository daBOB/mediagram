package designsystem

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Measured Colour Rule, held for every ground a viewer can actually
 * land text on: [Palette.Ground] (the window), [Palette.Page] (a plain
 * screen), [Palette.Sunk] (missing artwork), and [Palette.Sidebar] (a
 * settings index) — [Palette.Sidebar] wasn't a ground text had to clear
 * before it existed, so it joins the other three here rather than getting
 * its own test.
 */
class PaletteContrastTest {
    @Test
    fun everyDarkTextColourClearsTheFloorOnEveryDarkGround() {
        val grounds = listOf("Ground" to Palette.Ground, "Page" to Palette.Page, "Sunk" to Palette.Sunk, "Sidebar" to Palette.Sidebar)
        val texts = listOf("Text" to Palette.Text, "Figures" to Palette.Figures, "RuleStrong" to Palette.RuleStrong, "Ochre" to Palette.Ochre, "Sage" to Palette.Sage)
        assertEveryPairClearsTheFloor(texts, grounds)
    }

    @Test
    fun everyLightTextColourClearsTheFloorOnEveryLightGround() {
        val grounds =
            listOf(
                "LightGround" to Palette.LightGround,
                "LightPage" to Palette.LightPage,
                "LightSunk" to Palette.LightSunk,
                "LightSidebar" to Palette.LightSidebar,
            )
        val texts =
            listOf(
                "LightText" to Palette.LightText,
                "LightFigures" to Palette.LightFigures,
                "LightRuleStrong" to Palette.LightRuleStrong,
                "LightOchre" to Palette.LightOchre,
                "LightSage" to Palette.LightSage,
            )
        assertEveryPairClearsTheFloor(texts, grounds)
    }

    private fun assertEveryPairClearsTheFloor(
        texts: List<Pair<String, Color>>,
        grounds: List<Pair<String, Color>>,
    ) {
        for ((textName, text) in texts) {
            for ((groundName, ground) in grounds) {
                val ratio = contrast(text, ground)
                assertTrue(ratio >= MINIMUM_CONTRAST, "$textName reads $ratio on $groundName, need $MINIMUM_CONTRAST")
            }
        }
    }
}

package ui.tv.ambient

import androidx.core.graphics.ColorUtils
import ui.common.ambient.glowOf
import ui.common.ambient.strongestColourOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/** A poster's average made into a glow that reads on the dark page: never too dark or too pale, a little more coloured. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TvAmbientGlowTest {
    private fun hsl(argb: Int) = FloatArray(3).also { ColorUtils.colorToHSL(argb, it) }

    @Test
    fun aDarkMuddyAverageIsLiftedAndColouredMore() {
        val mud = android.graphics.Color.rgb(48, 38, 30)
        val glow = hsl(glowOf(mud))
        assertTrue(glow[2] >= 0.41f, "lightness ${glow[2]}, the band's floor less an 8-bit round trip")
        assertTrue(glow[1] > hsl(mud)[1], "saturation ${glow[1]}")
    }

    @Test
    fun aPaleAverageIsDimmedIntoTheBand() {
        val glow = hsl(glowOf(android.graphics.Color.rgb(240, 236, 230)))
        assertTrue(glow[2] <= 0.6f, "lightness ${glow[2]}")
    }

    @Test
    fun aPosterInMoreRedThanBlueGlowsRedNotTheGreyTheyAverageTo() {
        val red = android.graphics.Color.rgb(200, 30, 30)
        val blue = android.graphics.Color.rgb(30, 60, 200)
        val pixels = IntArray(60) { if (it < 35) red else blue }
        val hue = hsl(strongestColourOf(pixels))[0]
        assertTrue(hue < 20f || hue > 340f, "hue $hue")
        assertTrue(hsl(strongestColourOf(pixels))[1] > 0.5f, "a colour, not a grey")
    }

    @Test
    fun aBlackAndWhitePosterKeepsItsGrey() {
        val pixels = IntArray(40) { if (it % 2 == 0) android.graphics.Color.rgb(20, 20, 20) else android.graphics.Color.rgb(220, 220, 220) }
        val colour = hsl(strongestColourOf(pixels))
        assertTrue(colour[1] < 0.05f, "saturation ${colour[1]}")
    }
}

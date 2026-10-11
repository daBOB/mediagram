package ui.tv.settings

import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ClickableSurfaceColors
import designsystem.TvStyle
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.tv.catalog.TvScreenStateTest

/**
 * A focused or pressed swatch keeps the colour it previews, in every style:
 * tv-material's own focused fill, the style's light ink, blanked a Theme
 * swatch's "Aa" and an Artwork swatch's colour on the box.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TvSwatchColorsTest : TvScreenStateTest() {
    @Test
    fun aSwatchKeepsItsColourFocusedAndPressed() {
        val colour = Color(0xFF336699)
        for (style in TvStyle.entries) {
            var colours: ClickableSurfaceColors? = null
            show(style) { colours = swatchColors(colour) }
            // tv-material keeps the colour getters internal; its toString names every role.
            val roles = colours.toString()
            for (role in listOf("containerColor", "focusedContainerColor", "pressedContainerColor")) {
                assertTrue("$role=$colour," in roles, "$style: $role keeps the swatch's colour in $roles")
            }
            close()
        }
    }
}

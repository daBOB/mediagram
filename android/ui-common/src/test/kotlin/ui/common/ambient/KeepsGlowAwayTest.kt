package ui.common.ambient

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Over and next to the art the glow is gone, down to the page's own colour; a reach away, it is whole again. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KeepsGlowAwayTest {
    private val width = 400
    private val height = 300
    private val paper = Color(0xFF0D0D0E)
    private val glow = Color(0xFF6B6A2A)
    private val reach = 60f

    private fun render(block: DrawScope.() -> Unit): ImageBitmap =
        ImageBitmap(width, height).also {
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(it), Size(width.toFloat(), height.toFloat()), block)
        }

    /** The glow alone, cut around art from (150, 80) to the right edge and down to 200, then laid over the page. */
    private val pixels =
        render {
            drawRect(glow)
            cutGlowAround(Rect(150f, 80f, width.toFloat(), 200f), reach)
        }.let { glowLayer ->
            render {
                drawRect(paper)
                drawImage(glowLayer)
            }
        }.let { IntArray(width * height).also(it::readPixels) }

    private fun at(
        x: Int,
        y: Int,
    ) = pixels[y * width + x]

    private fun gap(
        a: Int,
        b: Color,
    ) = (0..24 step 8).maxOf { shift -> abs((a shr shift and 0xFF) - (b.toArgb() shr shift and 0xFF)) }

    @Test
    fun besideTheArtThePageColourHasTakenOver() {
        assertTrue(gap(at(149, 140), paper) <= 2, "left of the art")
        assertTrue(gap(at(300, 200), paper) <= 2, "below the art")
        assertTrue(gap(at(300, 79), paper) <= 2, "above the art")
        assertTrue(gap(at(149, 200), paper) <= 2, "at its lower corner")
        assertTrue(gap(at(300, 140), paper) <= 2, "under the art")
    }

    @Test
    fun aReachAwayTheGlowIsWholeAgain() {
        assertTrue(gap(at(89, 140), glow) <= 2, "left of the art")
        assertTrue(gap(at(300, 261), glow) <= 2, "below the art")
        assertTrue(gap(at(300, 19), glow) <= 2, "above the art")
        assertTrue(gap(at(105, 245), glow) <= 2, "past its lower corner")
    }
}

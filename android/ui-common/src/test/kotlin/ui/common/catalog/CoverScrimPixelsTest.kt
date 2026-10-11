package ui.common.catalog

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The cover scrim draws each layer only over the band where it shows. These
 * tests hold it to the pixels of the three full-size layers it replaced, on
 * an opaque backdrop so every blend counts, at the TV's own size and at
 * sizes whose band edges fall between pixels.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoverScrimPixelsTest {
    private val backdrop = Color(0xFFC0885A)

    private fun render(width: Int, height: Int, scrim: DrawScope.() -> Unit): IntArray {
        val bitmap = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
            drawRect(backdrop)
            scrim()
        }
        return IntArray(width * height).also { bitmap.readPixels(it) }
    }

    /** What `Modifier.background(brush)` drew per layer: the brush over the whole cover, back-to-front. */
    private fun DrawScope.fullSizeLayers(paper: Color) {
        drawRect(CoverTopScrim)
        drawRect(coverPaperFade(paper))
        drawRect(CoverSideScrim)
    }

    private fun largestChannelGap(a: IntArray, b: IntArray): Int =
        a.indices.maxOf { i -> (0..24 step 8).maxOf { shift -> abs((a[i] shr shift and 0xFF) - (b[i] shr shift and 0xFF)) } }

    /** Dark paper and a light one: the light theme is where a wrong layer order once showed. */
    private fun assertSamePixels(width: Int, height: Int) {
        for (paper in listOf(Color(0xFF0D0D0E), Color(0xFFF4EFE7))) {
            val expected = render(width, height) { fullSizeLayers(paper) }
            val banded = render(width, height) { drawCoverScrim(coverPaperFade(paper)) }
            assertEquals(0, largestChannelGap(expected, banded), "banded scrim at ${width}x$height on $paper")
        }
    }

    @Test
    fun atTheTelevisionsSizeTheBandedScrimDrawsTheFullSizeLayersPixels() = assertSamePixels(1920, 1080)

    @Test
    fun whereBandEdgesFallBetweenPixelsTheBandedScrimStillMatches() {
        assertSamePixels(411, 731)
        assertSamePixels(333, 187)
    }
}

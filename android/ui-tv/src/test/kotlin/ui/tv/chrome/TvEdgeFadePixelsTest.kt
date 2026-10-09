package ui.tv.chrome

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * The fade's own pixels, not only its decision: white content masked over
 * black, so the red channel at a pixel is the mask's alpha there. The end
 * band goes to black where the row clips; the centre, and an end that clips
 * nothing, stay white.
 *
 * Drawn into a bitmap through the view rather than with `captureToImage`,
 * whose redraw wait never completes under this project's Robolectric
 * (`ui-mobile`'s `PageGroundColorTest` says the same); the canvas is the
 * same native one, so an offscreen layer and its DstIn mask draw for real.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w640dp-h540dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvEdgeFadePixelsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var controller: ActivityController<ComponentActivity>? = null

    @After
    fun close() {
        compose.runOnUiThread { controller?.close() }
        controller = null
    }

    @Test
    fun theClippedEndFadesToNothingAndTheRestStaysWhole() {
        val alpha = alphasFor(EdgeFades(start = false, end = true))

        assertTrue(alpha.end < 0.1f, "the end band fades out: $alpha")
        assertTrue(alpha.centre > 0.9f && alpha.start > 0.9f, "the centre and the start stay whole: $alpha")
    }

    @Test
    fun withNothingClippedNoEndFades() {
        val alpha = alphasFor(EdgeFades(start = false, end = false))

        assertTrue(alpha.start > 0.9f && alpha.end > 0.9f, "nothing fades: $alpha")
    }

    private data class Alphas(val start: Float, val centre: Float, val end: Float)

    private fun alphasFor(fades: EdgeFades): Alphas {
        compose.runOnUiThread {
            val built = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller = built
            built.get().setContent {
                Box(Modifier.background(Color.Black)) {
                    Box(Modifier.testTag(ROW).fillMaxWidth().height(40.dp).fadeClippedEdges(fades).background(Color.White))
                }
            }
        }
        compose.waitForIdle()
        val row = compose.onNodeWithTag(ROW).fetchSemanticsNode().boundsInRoot
        lateinit var bitmap: Bitmap
        compose.runOnUiThread {
            val view: View = requireNotNull(controller).get().findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        val y = row.center.y.toInt()
        fun at(x: Float) = android.graphics.Color.red(bitmap.getPixel(x.toInt(), y)) / 255f
        return Alphas(start = at(row.left + 1), centre = at(row.center.x), end = at(row.right - 2))
    }

    private companion object {
        const val ROW = "row"
    }
}

package ui.tv

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.tv.material3.Border
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * [TvFocus.PillShape] and [TvFocus.ControlShape] threaded through
 * [TvFocus.fieldBorder] — the one function here whose return type
 * ([Border]) exposes its own `shape` publicly, so this is the one call
 * site the invariant `TvFocus.kt`'s own doc names ("container and ring cut
 * from the same shape") can actually be asserted on from outside the
 * module: `tv-material`'s own `CardShape`/`CardBorder`/
 * `ClickableSurfaceShape`/`ClickableSurfaceBorder` all keep their fields
 * `internal` to their own module, so [TvFocus.cardShape]/[TvFocus.cardBorder]/
 * [TvFocus.surfaceShape]/[TvFocus.surfaceBorder] read back nothing a test
 * outside `tv-material` can compare — those four are proven by their own
 * source instead: every branch below builds both the container and its
 * border from one `shape` parameter, never two.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TvFocusShapesTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var controller: ActivityController<ComponentActivity>? = null

    @After
    fun close() {
        compose.runOnUiThread { controller?.close() }
        controller = null
    }

    @Test
    fun fieldBorderCarriesWhicheverShapeItWasAskedFor() {
        for (shape in listOf(TvFocus.PillShape, TvFocus.ControlShape)) {
            lateinit var border: Border
            show { border = TvFocus.fieldBorder(focused = true, shape = shape) }

            assertEquals(shape, border.shape, "$shape carried through fieldBorder")
            close()
        }
    }

    /** The plate shape stays the default with no [shape] argument at all — every existing call site before this phase relied on exactly this. */
    @Test
    fun fieldBorderDefaultsToTheSquarePlateShape() {
        lateinit var border: Border
        show { border = TvFocus.fieldBorder(focused = false) }

        assertEquals(androidx.compose.ui.graphics.RectangleShape, border.shape)
    }

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.runOnUiThread {
            val built = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller = built
            built.get().setContent { TvTheme { content() } }
        }
        compose.waitForIdle()
    }
}

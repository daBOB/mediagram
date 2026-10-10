package designsystem

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Under "Remove animations" an entrance has nothing to play, so the page is
 * drawn at once, as the web's is: no blank first frame. A 100dp box centred
 * in a 200dp one rests at top = 50dp.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EntranceReducedMotionTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private val still = mutableStateOf(false)
    private val which = mutableIntStateOf(0)

    /** Composes [content] under [still] and runs the one frame that lays it out and places it. */
    private fun show(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                CompositionLocalProvider(LocalReducedMotion provides still.value) {
                    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) { content() }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    /** Another entrance in the last one's place, under [still], laid out and placed. */
    private fun swapTo(
        another: Int,
        still: Boolean,
    ) {
        compose.runOnUiThread {
            this.still.value = still
            which.intValue = another
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
    }

    @Composable private fun Entering() = Box(Modifier.size(100.dp).entrance(1100, fromY = 28.dp).testTag("e"))

    private fun top() = compose.onNodeWithTag("e").getBoundsInRoot().top.value

    /**
     * The alpha the node's layer draws with. Compose has no public read of a
     * layer's properties and no pixel capture works here, so this reaches the
     * one layer the entrance placed (`ModifierInfo.extra`) for the
     * [GraphicsLayer] it wraps.
     */
    private fun alpha(): Float {
        val layer = compose.onNodeWithTag("e").fetchSemanticsNode().layoutInfo.getModifierInfo().mapNotNull { it.extra }.single()
        val field = layer.javaClass.getDeclaredField("graphicsLayer").apply { isAccessible = true }
        return (field.get(layer) as GraphicsLayer).alpha
    }

    @Test
    fun underReducedMotionAnEntranceIsAtRestOnItsFirstFrame() {
        still.value = true
        show { Entering() }
        assertEquals(1f, alpha())
        assertEquals(50f, top(), 0.5f)
    }

    /** Read at each entrance, not once: the setting turned on or off reaches the next one. */
    @Test
    fun turningReducedMotionOnOrOffReachesTheNextEntrance() {
        show { key(which.intValue) { Entering() } }
        assertTrue(alpha() < 1f && top() > 55f, "rising in, alpha ${alpha()}, top ${top()}")

        swapTo(1, still = true)
        assertEquals(1f, alpha())
        assertEquals(50f, top(), 0.5f)

        swapTo(2, still = false)
        assertTrue(alpha() < 1f && top() > 55f, "rising in again, alpha ${alpha()}, top ${top()}")
    }
}

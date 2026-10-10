package designsystem

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
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
 * A lazy list cuts a parked item's tweens off wherever they were and hands the
 * node back with a new interaction source. It must come back at rest, not
 * half-lifted with nothing hovering it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HoverReuseTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    @Test
    fun aNodeParkedMidHoverComesBackAtRest() {
        var active by mutableStateOf(true)
        var source by mutableStateOf(MutableInteractionSource())
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                    ReusableContentHost(active) {
                        Box(Modifier.size(100.dp).hoverMotion(source, 700, lift = (-6).dp, zoom = 1.1f).testTag("h"))
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val first = source
        compose.runOnUiThread { first.tryEmit(HoverInteraction.Enter()) }
        compose.mainClock.advanceTimeBy(250)
        val bounds = { compose.onNodeWithTag("h").getBoundsInRoot() }
        assertTrue(bounds().top.value < 49.5f, "mid-hover when parked, top was ${bounds().top.value}")

        compose.runOnUiThread { active = false }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.runOnUiThread {
            source = MutableInteractionSource()
            active = true
        }
        compose.waitForIdle()
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        assertEquals(50f, bounds().top.value, 0.3f)
        assertEquals(100f, bounds().width.value, 0.3f)
    }
}

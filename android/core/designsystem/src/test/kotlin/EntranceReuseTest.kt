package designsystem

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
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
 * A lazy list parks an item it scrolled away by deactivating it, keeping its
 * nodes, and hands them back (to the same item or another) when one comes
 * into view. A 100dp box in a 200dp one rests at top = 50dp.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EntranceReuseTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) { content() } }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun top() = compose.onNodeWithTag("e").getBoundsInRoot().top.value

    @Test
    fun anEntranceParkedMidRiseComesBackAtRest() {
        var active by mutableStateOf(true)
        val seen = mutableStateOf(false)
        show { ReusableContentHost(active) { Box(Modifier.size(100.dp).entrance(1100, fromY = 28.dp, seen = seen).testTag("e")) } }
        compose.mainClock.advanceTimeBy(200)
        assertTrue(top() > 52f, "mid-rise when parked, top was ${top()}")
        compose.runOnUiThread { active = false }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.runOnUiThread { active = true }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(50f, top(), 0.5f)
    }

    @Test
    fun aNodeHandedToAnotherItemPlaysThatItemsEntrance() {
        var active by mutableStateOf(true)
        var seen by mutableStateOf(mutableStateOf(false))
        show { ReusableContentHost(active) { Box(Modifier.size(100.dp).entrance(1100, fromY = 28.dp, seen = seen).testTag("e")) } }
        compose.mainClock.advanceTimeBy(1_200)
        compose.runOnUiThread { active = false }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.runOnUiThread {
            seen = mutableStateOf(false)
            active = true
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertTrue(top() > 55f, "the other item rises in from below, top was ${top()}")
    }
}

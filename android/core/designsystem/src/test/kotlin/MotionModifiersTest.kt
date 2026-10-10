package designsystem

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
 * A 100dp box centred in a 200dp one rests at top = left = 50dp and
 * width = 100dp. Layer translation and scale show up in the node's
 * bounds in root, so the tests read motion from there; alpha does not,
 * and is left to the device walk (no pixel capture works here).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MotionModifiersTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(reducedMotion: Boolean = false, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
                    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) { content() }
                }
            }
        }
        // Two frames: one to compose, lay out and place; one for the nodes'
        // coroutines (animations, interaction collectors) to start.
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun top(tag: String) = compose.onNodeWithTag(tag).getBoundsInRoot().top.value
    private fun left(tag: String) = compose.onNodeWithTag(tag).getBoundsInRoot().left.value
    private fun width(tag: String) = compose.onNodeWithTag(tag).getBoundsInRoot().width.value

    @Test
    fun anEntranceRisesFromItsOffsetAndEndsAtRest() {
        show { Box(Modifier.size(100.dp).entrance(420, fromY = 10.dp).testTag("e")) }
        assertTrue(top("e") > 55f, "starts below its rest, top was ${top("e")}")
        compose.mainClock.advanceTimeBy(500)
        assertEquals(50f, top("e"), 0.5f)
    }

    @Test
    fun anEntranceScalesDownToRestAfterItsDelay() {
        show { Box(Modifier.size(100.dp).entrance(1400, delayMs = 200, fromScale = 1.04f).testTag("e")) }
        compose.mainClock.advanceTimeBy(150)
        assertEquals(104f, width("e"), 0.3f)
        compose.mainClock.advanceTimeBy(1600)
        assertEquals(100f, width("e"), 0.3f)
    }

    @Test
    fun anEntranceAlreadySeenStartsAtRest() {
        val seen = mutableStateOf(true)
        show { Box(Modifier.size(100.dp).entrance(1100, fromY = 28.dp, seen = seen).testTag("e")) }
        assertEquals(50f, top("e"), 0.5f)
    }

    @Test
    fun anEntranceMarksItselfSeenOncePlaced() {
        val seen = mutableStateOf(false)
        show { Box(Modifier.size(100.dp).entrance(1100, fromY = 28.dp, seen = seen).testTag("e")) }
        assertTrue(seen.value)
    }

    @Test
    fun aPausedDriftHoldsWhereItIsAndResumesFromThere() {
        var running by mutableStateOf(true)
        show { Box(Modifier.size(100.dp).drift(1f, 1.09f, 8_500, Motion.CssEaseOut, running = running).testTag("d")) }
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnUiThread { running = false }
        compose.mainClock.advanceTimeByFrame()
        val held = width("d")
        assertTrue(held > 100.5f && held < 108.5f, "mid-drift when paused, width was $held")
        compose.mainClock.advanceTimeBy(3_000)
        assertEquals(held, width("d"), 0.05f)
        compose.runOnUiThread { running = true }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(9_000)
        assertEquals(109f, width("d"), 0.2f)
    }

    @Test
    fun underReducedMotionADriftShowsItsRestScaleAndNeverMoves() {
        show(reducedMotion = true) {
            Box(Modifier.size(100.dp).drift(1f, 1.09f, 8_500, Motion.CssEaseOut, restScale = 1f).testTag("d"))
        }
        assertEquals(100f, width("d"), 0.05f)
        compose.mainClock.advanceTimeBy(10_000)
        assertEquals(100f, width("d"), 0.05f)
    }

    @Test
    fun aDriftRunsOnceAndStopsAtItsEnd() {
        show { Box(Modifier.size(100.dp).drift(1.06f, 1f, 14_000, Motion.EaseOut).testTag("d")) }
        assertTrue(width("d") > 105f)
        compose.mainClock.advanceTimeBy(15_000)
        assertEquals(100f, width("d"), 0.05f)
    }

    @Test
    fun aPressAloneNeverLiftsArtworkButAHoverDoes() {
        val source = MutableInteractionSource()
        show { Box(Modifier.size(100.dp).hoverMotion(source, 700, lift = (-6).dp).testTag("h")) }
        compose.runOnUiThread { source.tryEmit(PressInteraction.Press(Offset.Zero)) }
        compose.mainClock.advanceTimeBy(800)
        assertEquals(50f, top("h"), 0.3f)
        compose.runOnUiThread { source.tryEmit(HoverInteraction.Enter()) }
        compose.mainClock.advanceTimeBy(800)
        assertEquals(44f, top("h"), 0.3f)
    }

    @Test
    fun anArrowNudgesWhilePressedAndReturnsOnRelease() {
        val source = MutableInteractionSource()
        val press = PressInteraction.Press(Offset.Zero)
        show { Box(Modifier.size(100.dp).hoverMotion(source, 480, nudge = 4.dp).testTag("a")) }
        compose.runOnUiThread { source.tryEmit(press) }
        compose.mainClock.advanceTimeBy(600)
        assertEquals(54f, left("a"), 0.3f)
        compose.runOnUiThread { source.tryEmit(PressInteraction.Release(press)) }
        compose.mainClock.advanceTimeBy(600)
        assertEquals(50f, left("a"), 0.3f)
    }

    @Test
    fun aHoverZoomGrowsTheArtworkAroundItsCentre() {
        val source = MutableInteractionSource()
        show { Box(Modifier.size(100.dp).hoverMotion(source, 1100, zoom = 1.05f).testTag("z")) }
        compose.runOnUiThread { source.tryEmit(HoverInteraction.Enter()) }
        compose.mainClock.advanceTimeBy(1200)
        assertEquals(105f, width("z"), 0.3f)
        assertEquals(47.5f, left("z"), 0.3f)
    }
}

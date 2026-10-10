package designsystem

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * The numeral is read through [CountdownNumeral], the leader's test-only
 * semantics property: the sweep and the figures are drawn, and no pixel
 * capture works under this Robolectric (`MotionModifiersTest`'s note).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CountdownLeaderTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(reducedMotion: MutableState<Boolean>) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                CompositionLocalProvider(LocalReducedMotion provides reducedMotion.value) { CountdownLeader() }
            }
        }
        // One frame to compose and draw, which is when the clock starts.
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun numeral(): Int {
        compose.waitForIdle()
        return compose.onNodeWithTag(COUNTDOWN_LEADER_TEST_TAG).fetchSemanticsNode().config[CountdownNumeral]
    }

    private fun numeralAfter(ms: Long): Int {
        compose.mainClock.advanceTimeBy(ms)
        return numeral()
    }

    private fun numeralAfterFlipping(reducedMotion: MutableState<Boolean>, to: Boolean): Int {
        compose.runOnUiThread { reducedMotion.value = to }
        compose.mainClock.advanceTimeByFrame()
        return numeral()
    }

    @Test fun itComposesOnTheThree() {
        show(mutableStateOf(false))
        assertEquals(3, numeral())
    }

    @Test fun eachCompletedTurnStepsTheCountDownAndItLoops() {
        show(mutableStateOf(false))
        assertEquals(2, numeralAfter(1100))
        assertEquals(1, numeralAfter(1000))
        assertEquals(3, numeralAfter(1000))
    }

    @Test fun underReducedMotionNothingMovesAndTheThreeHolds() {
        show(mutableStateOf(true))
        assertEquals(3, numeralAfter(1100))
        assertEquals(3, numeralAfter(1000))
    }

    /** "Remove animations" turned on mid-count stops the clock at once; turned off, the count starts again from 3. */
    @Test fun turningReducedMotionOnAndOffStopsAndRestartsTheCount() {
        val still = mutableStateOf(false)
        show(still)
        assertEquals(2, numeralAfter(1100))
        assertEquals(3, numeralAfterFlipping(still, to = true))
        assertEquals(3, numeralAfter(2100))
        assertEquals(3, numeralAfterFlipping(still, to = false))
        assertEquals(2, numeralAfter(1100))
        assertEquals(1, numeralAfter(1000))
    }
}

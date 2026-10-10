package designsystem

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
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

    private fun show(reducedMotion: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                CompositionLocalProvider(LocalReducedMotion provides reducedMotion) { CountdownLeader() }
            }
        }
        // One frame to compose and draw, which is when the clock starts.
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun numeralAfter(ms: Long): Int {
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
        return compose.onNodeWithTag(COUNTDOWN_LEADER_TEST_TAG).fetchSemanticsNode().config[CountdownNumeral]
    }

    @Test fun itComposesOnTheThree() {
        show(reducedMotion = false)
        assertEquals(3, numeralAfter(0))
    }

    @Test fun eachCompletedTurnStepsTheCountDownAndItLoops() {
        show(reducedMotion = false)
        assertEquals(2, numeralAfter(1100))
        assertEquals(1, numeralAfter(1000))
        assertEquals(3, numeralAfter(1000))
    }

    @Test fun underReducedMotionNothingMovesAndTheThreeHolds() {
        show(reducedMotion = true)
        assertEquals(3, numeralAfter(1100))
        assertEquals(3, numeralAfter(1000))
    }
}

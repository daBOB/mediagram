package playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgressThrottleTest {

    @Test
    fun theFirstAskAlwaysEmits() {
        val throttle = ProgressThrottle(minIntervalMs = 250L, clock = { 0L })

        assertTrue(throttle.shouldEmit())
    }

    @Test
    fun aSecondAskBeforeTheIntervalIsSuppressed() {
        var now = 0L
        val throttle = ProgressThrottle(minIntervalMs = 250L, clock = { now })

        assertTrue(throttle.shouldEmit())
        now = 100L
        assertFalse(throttle.shouldEmit())
    }

    @Test
    fun anAskAfterTheIntervalEmitsAgain() {
        var now = 0L
        val throttle = ProgressThrottle(minIntervalMs = 250L, clock = { now })
        throttle.shouldEmit()

        now = 250L

        assertTrue(throttle.shouldEmit())
    }

    @Test
    fun manyCallbacksWithinOneIntervalOnlyEmitOnce() {
        var now = 0L
        val throttle = ProgressThrottle(minIntervalMs = 250L, clock = { now })

        val emitted = (1..20).count { now += 10; throttle.shouldEmit() }

        // 20 calls, 10ms apart, span 200ms — under the 250ms interval, so
        // only the very first one (which always emits) gets through.
        assertEquals(1, emitted)
    }
}

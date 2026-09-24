package player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A port of `autoplay.test.ts`. */
class AutoplayTest {

    private fun at(ahead: Double = 0.0, remaining: Double? = 1200.0, waitedMs: Long = 0) =
        AutoplayAt(ahead, remaining, waitedMs)

    @Test
    fun waitsForTheMinute() {
        assertFalse(autoplayReady(at(ahead = 59.0)))
        assertTrue(autoplayReady(at(ahead = READY_SECONDS)))
    }

    /** A forty second lesson never buffers sixty seconds ahead of itself, and waiting for it would hang until the patience ran out every time. */
    @Test
    fun doesNotWaitForAMinuteAShortTitleCannotHold() {
        assertTrue(autoplayReady(at(ahead = 40.0, remaining = 40.0)))
        assertFalse(autoplayReady(at(ahead = 20.0, remaining = 40.0)))
    }

    /** A slow encoder delays the next title; it must not cancel it. */
    @Test
    fun goesAnywayOnceItHasWaitedLongEnough() {
        assertFalse(autoplayReady(at(ahead = 3.0, waitedMs = PATIENCE_MS - 1)))
        assertTrue(autoplayReady(at(ahead = 3.0, waitedMs = PATIENCE_MS)))
    }

    @Test
    fun keepsWaitingWhileTheRuntimeIsUnknownAndTheBufferIsShort() {
        assertFalse(autoplayReady(at(ahead = 10.0, remaining = null)))
        // The minute still ends the wait, runtime or no runtime.
        assertTrue(autoplayReady(at(ahead = 61.0, remaining = null)))
    }

    /**
     * `remaining` is 0 at the very end of the previous title's clock; it
     * must not read as "everything left is buffered" when nothing is.
     */
    @Test
    fun isNotFooledByARemainingOfZeroIntoStartingOnAnEmptyBuffer() {
        assertFalse(autoplayReady(at(ahead = 0.0, remaining = 0.0)))
    }
}

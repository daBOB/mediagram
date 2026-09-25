package player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** A port of `up-next.test.ts`. */
class UpNextTest {

    private fun at(
        hasNext: Boolean = true,
        cancelled: Boolean = false,
        remainingSeconds: Double? = 600.0,
        ended: Boolean = false,
    ) = UpNextAt(hasNext, cancelled, remainingSeconds, ended)

    @Test
    fun isNotOfferedInTheMiddleOfATitle() {
        assertEquals(UpNextPhase.HIDDEN, upNextPhase(at()))
    }

    @Test
    fun isOfferedAsAHeadsUpNearTheEnd() {
        assertEquals(UpNextPhase.WAITING, upNextPhase(at(remainingSeconds = WARN_SECONDS)))
        assertEquals(UpNextPhase.WAITING, upNextPhase(at(remainingSeconds = 5.0)))
    }

    /**
     * The bug: the panel appeared 30s out carrying a 10s timer, so the next
     * episode began with 20 seconds of this one still to play.
     */
    @Test
    fun neverCountsDownBeforeTheTitleHasEnded() {
        for (remaining in listOf(WARN_SECONDS, 20.0, 5.0, 1.0, 0.0)) {
            assertNotEquals(UpNextPhase.COUNTING, upNextPhase(at(remainingSeconds = remaining)))
        }
    }

    @Test
    fun countsDownOnceItHasEnded() {
        assertEquals(UpNextPhase.COUNTING, upNextPhase(at(ended = true, remainingSeconds = 0.0)))
    }

    @Test
    fun countsDownOnAnEndThatArrivedBySeekingPastTheLastFrame() {
        assertEquals(UpNextPhase.COUNTING, upNextPhase(at(ended = true, remainingSeconds = 600.0)))
    }

    @Test
    fun nothingFollowsThisTitle() {
        assertEquals(UpNextPhase.HIDDEN, upNextPhase(at(hasNext = false, remainingSeconds = 2.0)))
        assertEquals(UpNextPhase.HIDDEN, upNextPhase(at(hasNext = false, ended = true)))
    }

    @Test
    fun theViewerCancelledItWhichIsRememberedForThisTitle() {
        assertEquals(UpNextPhase.HIDDEN, upNextPhase(at(cancelled = true, remainingSeconds = 2.0)))
        // Cancelling must survive the end, or the countdown would arrive anyway.
        assertEquals(UpNextPhase.HIDDEN, upNextPhase(at(cancelled = true, ended = true)))
    }

    @Test
    fun theRuntimeIsUnknownSoThereIsNothingToCountDownFrom() {
        assertEquals(UpNextPhase.HIDDEN, upNextPhase(at(remainingSeconds = null)))
        assertEquals(UpNextPhase.HIDDEN, upNextPhase(at(remainingSeconds = Double.NaN)))
        // The end still catches it, whatever the runtime said.
        assertEquals(UpNextPhase.COUNTING, upNextPhase(at(remainingSeconds = null, ended = true)))
    }

    @Test
    fun nextInQueueWalksOneStepForwardAndStopsAtTheEnd() {
        val run = listOf("a", "b", "c")
        assertEquals("b", nextInQueue(run, "a"))
        assertEquals("c", nextInQueue(run, "b"))
        assertEquals(null, nextInQueue(run, "c"))
        assertEquals(null, nextInQueue(run, "not-here"))
    }
}

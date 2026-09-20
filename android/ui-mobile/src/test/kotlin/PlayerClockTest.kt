package ui

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The same shape the web player prints, because a viewer who uses both should
 * not have to read two clocks. `format.js` is the reference.
 */
class PlayerClockTest {

    @Test
    fun anEpisodeIsMinutesAndSeconds() {
        assertEquals("58:02", clockTime(3_482_000))
    }

    @Test
    fun aFilmGainsAnHourAndPadsTheMinutes() {
        assertEquals("1:58:02", clockTime(7_082_000))
    }

    /** Padded to `0:58:02` it reads as a stopwatch rather than a running time. */
    @Test
    fun anHourThatIsNotThereIsNotPrinted() {
        assertEquals("0:09", clockTime(9_000))
    }

    /** Truncated, not rounded: a clock shows 12:34 until 12:35 has arrived. */
    @Test
    fun aPartSecondHasNotHappenedYet() {
        assertEquals("0:12", clockTime(12_999))
    }

    /**
     * `durationMs` is `C.TIME_UNSET` — a large negative — until the player
     * knows the length, and the bar is drawn before it does.
     */
    @Test
    fun aLengthNobodyKnowsYetIsZero() {
        assertEquals("0:00", clockTime(Long.MIN_VALUE))
    }
}

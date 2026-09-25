package model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The one clock format both surfaces print. Mirrors the web player's
 * `format.js`.
 */
class ClockTimeTest {
    @Test
    fun anEpisodeIsMinutesAndSeconds() {
        assertEquals("58:02", clockTime(3_482.0))
    }

    @Test
    fun aFilmGainsAnHourAndPadsTheMinutes() {
        assertEquals("1:58:02", clockTime(7_082.0))
    }

    /** Padded to `0:58:02` it reads as a stopwatch rather than a running time. */
    @Test
    fun anHourThatIsNotThereIsNotPrinted() {
        assertEquals("0:09", clockTime(9.0))
    }

    /** Truncated, not rounded: a clock shows 12:34 until 12:35 has arrived. */
    @Test
    fun aPartSecondHasNotHappenedYet() {
        assertEquals("0:12", clockTime(12.999))
    }

    /** A runtime the catalog does not know yet, the same as media3's `C.TIME_UNSET`. */
    @Test
    fun aLengthNobodyKnowsYetIsZero() {
        assertEquals("0:00", clockTime(Double.NEGATIVE_INFINITY))
        assertEquals("0:00", clockTime(-1.0))
        assertEquals("0:00", clockTime(0.0))
    }
}

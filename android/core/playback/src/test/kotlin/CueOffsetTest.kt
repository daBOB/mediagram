package playback

import kotlin.test.Test
import kotlin.test.assertEquals

/** Ported from the web's `subtitle-panel.js` (`clamp`, `nudge`) and its own reset button. */
class CueOffsetTest {

    // -- clamping --

    @Test
    fun clampsToThirtySecondsEitherWay() {
        assertEquals(30.0, clampCueOffsetSeconds(45.0))
        assertEquals(-30.0, clampCueOffsetSeconds(-45.0))
    }

    @Test
    fun withinRangeIsLeftAlone() {
        assertEquals(12.3, clampCueOffsetSeconds(12.3))
    }

    @Test
    fun aNonFiniteValueIsNought() {
        assertEquals(0.0, clampCueOffsetSeconds(Double.NaN))
        assertEquals(0.0, clampCueOffsetSeconds(Double.POSITIVE_INFINITY))
    }

    // -- nudging --

    @Test
    fun oneStepMovesATenthOfASecond() {
        assertEquals(0.1, nudgeCueOffsetSeconds(0.0, steps = 1))
        assertEquals(-0.1, nudgeCueOffsetSeconds(0.0, steps = -1))
    }

    @Test
    fun fourTapsIsFourTenthsNotFloatingPointDrift() {
        var value = 0.0
        repeat(4) { value = nudgeCueOffsetSeconds(value, steps = 1) }
        assertEquals(0.4, value)
    }

    @Test
    fun nudgingPastTheLimitClamps() {
        assertEquals(30.0, nudgeCueOffsetSeconds(29.95, steps = 1))
    }

    // -- what is remembered --

    @Test
    fun theStoredValueOrNoughtForAnythingUnparseable() {
        assertEquals(1.5, cueOffsetSecondsOrDefault("1.5"))
        assertEquals(0.0, cueOffsetSecondsOrDefault("soon"))
        assertEquals(0.0, cueOffsetSecondsOrDefault(null))
    }

    @Test
    fun aStoredValueBeyondTheLimitIsStillClamped() {
        // Defends against a value some other client (or a hand-edited row)
        // wrote outside the range this sheet itself would ever produce.
        assertEquals(30.0, cueOffsetSecondsOrDefault("999"))
    }

    // -- the readout --

    @Test
    fun signedSoAPastNoughtNudgeIsVisiblyDifferentFromOneThatNeverLeftIt() {
        assertEquals("+0.3s", cueOffsetLabel(0.3))
        assertEquals("-1.0s", cueOffsetLabel(-1.0))
        assertEquals("0.0s", cueOffsetLabel(0.0))
    }
}

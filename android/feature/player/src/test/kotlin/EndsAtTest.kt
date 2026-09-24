package player

import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/** Covers [endsAtClock] and [endsAtLabel] — ported from `web/test/format.test.ts`'s "when it ends". */
class EndsAtTest {

    // Pinned to UTC rather than the JVM's default, so the test is
    // deterministic wherever it runs — production code still defaults to
    // the device's own zone.
    private fun at(hour: Int, minute: Int): Long =
        ZonedDateTime.of(2026, 9, 18, hour, minute, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun addsWhatIsLeftToTheClock() {
        // 1h 59m of Blade left at 20:42.
        assertEquals("22:41", endsAtClock(7142.0, at(20, 42), ZoneOffset.UTC))
        assertEquals("20:42", endsAtClock(0.0, at(20, 42), ZoneOffset.UTC))
    }

    @Test
    fun rollsOverMidnightRatherThanCountingPastIt() {
        assertEquals("00:30", endsAtClock(3600.0, at(23, 30), ZoneOffset.UTC))
        assertEquals("01:10", endsAtClock(7200.0, at(23, 10), ZoneOffset.UTC))
    }

    @Test
    fun padsSoTheFiguresLineUpWithTheOnesBeside() {
        assertEquals("09:05", endsAtClock(60.0, at(9, 4), ZoneOffset.UTC))
    }

    @Test
    fun saysNothingRatherThanGuessingWhenTheRuntimeIsUnknown() {
        assertEquals("", endsAtClock(Double.NaN, at(20, 0), ZoneOffset.UTC))
        assertEquals("", endsAtClock(-1.0, at(20, 0), ZoneOffset.UTC))
        assertEquals("", endsAtClock(Double.POSITIVE_INFINITY, at(20, 0), ZoneOffset.UTC))
    }

    @Test
    fun theLabelDividesTheRemainderBySpeed() {
        // 2h left at 1x is 2h; at 2x it is 1h.
        val label1x = endsAtLabel(7200.0, 0.0, 1f, at(20, 0), ZoneOffset.UTC)
        val label2x = endsAtLabel(7200.0, 0.0, 2f, at(20, 0), ZoneOffset.UTC)

        assertEquals("ends 22:00", label1x)
        assertEquals("ends 21:00", label2x)
    }

    @Test
    fun theLabelIsBlankWithNoKnownRuntime() {
        assertEquals("", endsAtLabel(null, 0.0, 1f, at(20, 0), ZoneOffset.UTC))
        assertEquals("", endsAtLabel(0.0, 0.0, 1f, at(20, 0), ZoneOffset.UTC))
    }

    @Test
    fun anInvalidSpeedFallsBackToOne() {
        assertEquals(
            endsAtLabel(3600.0, 0.0, 1f, at(20, 0), ZoneOffset.UTC),
            endsAtLabel(3600.0, 0.0, 0f, at(20, 0), ZoneOffset.UTC),
        )
    }
}

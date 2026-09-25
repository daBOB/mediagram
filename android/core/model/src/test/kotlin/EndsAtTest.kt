package model

import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

/** The end time the player prints beside its clock. Mirrors the web player's `format.js`. */
class EndsAtTest {
    private val eightPm = Instant.parse("2026-09-25T20:00:00Z")

    @Test
    fun anHourAndAHalfFromEightIsHalfPastNine() {
        assertEquals("21:30", endsAt(5_400.0, eightPm, ZoneOffset.UTC))
    }

    @Test
    fun theHourAndMinutesArePadded() {
        assertEquals("08:05", endsAt(300.0, Instant.parse("2026-09-25T08:00:00Z"), ZoneOffset.UTC))
    }

    /** Past midnight is the next morning, not the twenty-fifth hour. */
    @Test
    fun aFilmEndingAfterMidnightRollsOver() {
        assertEquals("00:20", endsAt(4 * 3_600.0 + 20 * 60, eightPm, ZoneOffset.UTC))
    }

    @Test
    fun theViewersOwnZoneDecidesTheHour() {
        assertEquals("22:00", endsAt(0.0, eightPm, ZoneOffset.ofHours(2)))
    }

    @Test
    fun anUnknownOrNegativeRemainderSaysNothing() {
        assertEquals("", endsAt(Double.NaN, eightPm, ZoneOffset.UTC))
        assertEquals("", endsAt(Double.POSITIVE_INFINITY, eightPm, ZoneOffset.UTC))
        assertEquals("", endsAt(-1.0, eightPm, ZoneOffset.UTC))
    }
}

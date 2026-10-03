package stats

import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class StatsFormatTest {
    @Test
    fun durationsCountWholeMinutes() {
        assertEquals("under a minute", durationText(0.0))
        assertEquals("under a minute", durationText(59.9))
        assertEquals("1 min", durationText(60.0))
        assertEquals("42 min", durationText(42 * 60 + 59.0))
        assertEquals("59 min", durationText(3_599.0))
        assertEquals("1 h", durationText(3_600.0))
        assertEquals("3 h", durationText(3 * 3_600 + 59.0))
        assertEquals("3 h 12 min", durationText(3 * 3_600 + 12 * 60 + 30.0))
    }

    @Test
    fun aTimeIsTodayThenAWeekdayForSixDaysThenADate() {
        assertEquals("today 21:14", whenText(ms(2026, 9, 26, 21, 14), Now))
        assertEquals("today 00:05", whenText(ms(2026, 9, 26, 0, 5), Now))
        assertEquals("Fri 20:05", whenText(ms(2026, 9, 25, 20, 5), Now))
        assertEquals("Sun 09:03", whenText(ms(2026, 9, 20, 9, 3), Now), "six days back is still a weekday")
        assertEquals("19 Sep", whenText(ms(2026, 9, 19, 23, 59), Now), "seven days back would repeat today's weekday")
        assertEquals("3 Jan", whenText(ms(2026, 1, 3, 12, 0), Now), "the day is unpadded")
        assertEquals("21 Sep 2025", whenText(ms(2025, 9, 21, 18, 30), Now))
    }

    @Test
    fun aTimeIsToldOnThisDevicesOwnClock() {
        // 22:30 UTC on Friday is already half past midnight on Saturday in Berlin.
        val at = ZonedDateTime.of(2026, 9, 25, 22, 30, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals("today 00:30", whenText(at, Now))
    }

    @Test
    fun weekdayLettersRunMondayToSundayAndBarDatesCarryNoYear() {
        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), (21..27).map { weekdayInitial(LocalDate.of(2026, 9, it)) })
        assertEquals("3 Oct", shortDate(LocalDate.of(2026, 10, 3)))
        assertEquals("31 Dec", shortDate(LocalDate.of(2025, 12, 31)))
    }

    @Test
    fun aFailureSaysWhyWhenThereIsAReason() {
        assertEquals("Could not read your stats: database is locked", failureLine("database is locked"))
        assertEquals("Could not read your stats.", failureLine(null))
        assertEquals("Could not read your stats.", failureLine(" "))
    }
}

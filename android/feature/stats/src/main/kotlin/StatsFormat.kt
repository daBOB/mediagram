package stats

import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/*
 * The Stats page's words, the web's exactly (stats-format.js). Hand-rolled
 * English rather than the device's locale, as the player's own clock is
 * (EndsAt.kt): a viewer who reads the page on the web and on a television
 * reads the same line on both.
 */

private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** Watch time in whole minutes, floored: "under a minute" (0 s included), "42 min", "3 h 12 min", "3 h". */
fun durationText(seconds: Double): String {
    val minutes = (seconds / 60).toLong()
    return when {
        minutes < 1 -> "under a minute"
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0L -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}

/**
 * When something happened, told on [now]'s own clock and zone: "today
 * 21:14"; a weekday for the six calendar days before ("Sat 21:14") — a
 * seventh would repeat today's weekday; a date further back ("21 Sep"),
 * with the year once it is another year's. A time ahead of [now] (another
 * device's clock running fast) reads as its date.
 */
fun whenText(
    atMs: Long,
    now: ZonedDateTime,
): String {
    val at = Instant.ofEpochMilli(atMs).atZone(now.zone)
    val clock = "%02d:%02d".format(at.hour, at.minute)
    return when (ChronoUnit.DAYS.between(at.toLocalDate(), now.toLocalDate())) {
        0L -> "today $clock"
        in 1L..6L -> "${WEEKDAYS[at.dayOfWeek.value - 1]} $clock"
        else -> if (at.year == now.year) shortDate(at.toLocalDate()) else "${shortDate(at.toLocalDate())} ${at.year}"
    }
}

/** "3 Oct": the day unpadded, no year — a bar's label, and the start of an older history time. */
fun shortDate(date: LocalDate): String = "${date.dayOfMonth} ${MONTHS[date.monthValue - 1]}"

/** The letter under a day's bar, Monday first: M T W T F S S. */
fun weekdayInitial(date: LocalDate): String = WEEKDAYS[date.dayOfWeek.value - 1].take(1)

/** The page's line when the read failed, with the platform's reason when it gave one. */
fun failureLine(reason: String?): String = if (reason.isNullOrBlank()) "Could not read your stats." else "Could not read your stats: $reason"

package model

import java.time.Instant
import java.time.ZoneId

/**
 * The wall-clock time something with [remainingSeconds] left will finish,
 * as `21:40`. Mirrors `endsAt` in the web player's `format.js`.
 *
 * Takes the clock rather than reading it, so a test can assert on the
 * answer and a paused film asked the same question again gets a later
 * answer each time — which is the truth about a paused film.
 *
 * Hand-rolled 24-hour rather than a locale's own time format, for the same
 * reason [clockTime] is: it sits beside tabular figures that do not change
 * with the host's locale, and the two surfaces should print the same thing.
 * Empty for a remainder that is not a finite, non-negative number, because
 * an end time projected from nothing is a guess dressed as a fact.
 */
fun endsAt(
    remainingSeconds: Double,
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    if (!remainingSeconds.isFinite() || remainingSeconds < 0) return ""
    // The zone carries the rollover, so a film finishing after midnight
    // says 00:20 rather than 24:20.
    val end = now.plusMillis((remainingSeconds * 1_000).toLong()).atZone(zone)
    return "${end.hour.toString().padStart(2, '0')}:${end.minute.toString().padStart(2, '0')}"
}

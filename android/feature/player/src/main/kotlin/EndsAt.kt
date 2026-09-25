package player

import data.ResumePoint
import java.time.Instant
import java.time.ZoneId

/**
 * The wall-clock time something with [remainingSeconds] left will finish —
 * a port of the web's `endsAt` (`format.js`). Hand-rolled 24-hour rather
 * than a locale-formatted string, for the same reason the web avoids
 * `toLocaleTimeString`: the format should not change with the device's
 * locale when it sits beside a scrub bar's own digits, which do not.
 *
 * Blank rather than a guess for anything that is not a real countdown —
 * `NaN`, negative, or infinite.
 */
fun endsAtClock(remainingSeconds: Double, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    if (!remainingSeconds.isFinite() || remainingSeconds < 0) return ""
    val end = Instant.ofEpochMilli(nowMs + (remainingSeconds * 1000).toLong()).atZone(zone)
    return "%02d:%02d".format(end.hour, end.minute)
}

/**
 * The label beside the transport bar's own clock: `ends HH:MM`.
 *
 * Divided by [speed] so a viewer at 1.5x is told the truth, and blank when
 * [runtimeSeconds] is unknown — a projected end time from an unknown
 * length is a guess wearing the clothes of a fact. Android always plays
 * the original file directly (never a transcode whose reported length is
 * still growing, the case the web's own `refreshEnds` guards against by
 * reading the catalogued runtime instead of the element's), so the
 * caller may pass media3's own duration here without the web's caveat.
 */
fun endsAtLabel(
    runtimeSeconds: Double?,
    positionSeconds: Double,
    speed: Float,
    nowMs: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    if (runtimeSeconds == null || runtimeSeconds <= 0) return ""
    val rate = if (speed.isFinite() && speed > 0f) speed else 1f
    val remaining = maxOf(0.0, runtimeSeconds - positionSeconds) / rate
    val clock = endsAtClock(remaining, nowMs, zone)
    return if (clock.isEmpty()) "" else "ends $clock"
}

/**
 * The end-time line both players draw beside their clock, from what a
 * player has on hand: the catalogue's runtime ([cataloguedSecs]) trusted
 * over media3's own length ([durationMs]) until it has one — see
 * [ResumePoint.trustedRuntime] — so the line is there before media3 has
 * buffered enough to report a length, and blank when neither knows it.
 * [positionMs] is the playhead, never a scrub thumb, which only previews
 * where a seek would land.
 */
fun endsLine(
    cataloguedSecs: Int?,
    positionMs: Long,
    durationMs: Long,
    speed: Float,
    nowMs: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val runtimeSeconds =
        ResumePoint
            .trustedRuntime(
                catalogued = cataloguedSecs?.toDouble(),
                observed = durationMs.takeIf { it > 0 }?.let { it / 1_000.0 },
                direct = true,
            ).takeIf { it > 0 }
    return endsAtLabel(
        runtimeSeconds = runtimeSeconds,
        positionSeconds = positionMs.coerceAtLeast(0L) / 1_000.0,
        speed = speed,
        nowMs = nowMs,
        zone = zone,
    )
}

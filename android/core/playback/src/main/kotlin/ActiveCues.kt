package playback

/**
 * One subtitle line, already parsed: a start and end in milliseconds and its
 * plain text, before any offset is applied. [SubtitleTrack.kt] is what
 * produces these; nothing here reads a VTT file directly.
 */
data class TimedCue(val startMs: Long, val endMs: Long, val text: String)

/**
 * [cue] moved by [offsetMs] — a port of the web's `shiftedTimes`
 * (`subtitle-style.js`). Computed fresh from the parsed times every call
 * rather than accumulated onto a mutable cue, so nudging the offset twice is
 * never nudging it twice over: there is nothing here for a second call to
 * drift away from.
 *
 * Both ends clamp at nought, because a cue cannot start before the film
 * does, and the end never precedes the (already clamped) start however far
 * back the offset reaches — a cue squashed to nothing near the beginning is
 * the honest outcome; the alternative is showing it at a time it was never
 * meant for.
 */
fun shiftedTimes(cue: TimedCue, offsetMs: Long): TimedCue {
    val start = (cue.startMs + offsetMs).coerceAtLeast(0L)
    val end = (cue.endMs + offsetMs).coerceAtLeast(start)
    return cue.copy(startMs = start, endMs = end)
}

/**
 * Every one of [cues] on screen at [positionMs], each measured from its own
 * parsed times and [offsetMs] — never from wherever a previous call left it.
 * A cue is active from its (shifted) start up to, but not including, its end.
 */
fun activeCues(cues: List<TimedCue>, positionMs: Long, offsetMs: Long): List<TimedCue> =
    cues.mapNotNull { cue ->
        val shifted = shiftedTimes(cue, offsetMs)
        shifted.takeIf { positionMs >= it.startMs && positionMs < it.endMs }
    }

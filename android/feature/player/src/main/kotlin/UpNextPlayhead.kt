package player

/*
 * Where the playhead stands against the open title's end, as
 * [UpNextController] reads it.
 */

/**
 * Seconds left in a title [runtimeSecs] long, or `null` for a runtime this
 * device does not know — kept apart from a real zero, which is the one value
 * that would otherwise open the card at once.
 */
internal fun PlayerHandle.remainingSeconds(runtimeSecs: Int?): Double? {
    val runtime = runtimeSecs?.toDouble() ?: return null
    if (runtime <= 0) return null
    val posMs = positionMs() ?: return null
    return runtime - posMs / 1_000.0
}

/**
 * Whether the playhead sits on the title's last frame. `true` when either
 * reading is missing: a seek nothing can measure leaves an ending as it was
 * rather than guessing it away.
 */
internal fun PlayerHandle.isAtEnd(): Boolean {
    val position = positionMs() ?: return true
    val length = durationMs() ?: return true
    return position >= length - END_SLACK_MS
}

/** A clamped skip lands on the length exactly; a second's slack covers a player that rounds it to a frame. */
private const val END_SLACK_MS = 1_000L

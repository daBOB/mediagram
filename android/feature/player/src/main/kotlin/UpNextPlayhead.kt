package player

/*
 * Where the playhead stands against the open title's end, as
 * [UpNextController] reads it — split out of that file to keep it under
 * the project's line guideline.
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

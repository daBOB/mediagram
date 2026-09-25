package player

/**
 * When a title that started itself may actually begin — a port of
 * `autoplay.js`. A viewer who pressed play is watching the screen and wants
 * the picture; one whose episode ended thirty seconds ago is not, and would
 * rather the next one arrive whole than arrive immediately and stop again
 * ten seconds in. So an unattended start waits for a buffer, and an
 * asked-for one does not.
 */

/** The buffer an unattended start waits for. */
const val READY_SECONDS = 60.0

/**
 * How long it may wait for that before going anyway.
 *
 * A slow encoder should delay the next title, not cancel it: giving up on
 * the wait still plays, it just plays with less in hand.
 */
const val PATIENCE_MS = 45_000L

data class AutoplayAt(
    /** How far playback is buffered ahead of the playhead, in seconds. */
    val aheadSeconds: Double,
    /** Seconds left in the title, or `null` for an unknown runtime. */
    val remainingSeconds: Double?,
    val waitedMs: Long,
)

fun autoplayReady(at: AutoplayAt): Boolean {
    if (at.aheadSeconds.isFinite() && at.aheadSeconds >= READY_SECONDS) return true

    // A forty second lesson can never hold a minute ahead of itself. Having
    // all of what is left is the same promise as having a minute of it.
    val left = at.remainingSeconds
    if (left != null && left.isFinite() && left > 0 && at.aheadSeconds.isFinite() && at.aheadSeconds >= left - 1) {
        return true
    }

    // Out of patience. Whatever is held is what it starts with.
    return at.waitedMs >= PATIENCE_MS
}

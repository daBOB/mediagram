package player

/**
 * When a title held back for its buffer may actually begin — a port of
 * `autoplay.js`. On the web only an unattended start waits; here every
 * title opened warms up first (`UpNextSwitcher.warmUp`), because a viewer
 * chose a picture that arrives whole over one that arrives at once and
 * then stalls. Pressing play still starts at once.
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

/**
 * How far media3's default load control ever buffers ahead
 * (`DefaultLoadControl.DEFAULT_MAX_BUFFER_MS`): the point at which its
 * loader stops, which is what a warm-up on this platform waits for — the
 * web's 60 s [READY_SECONDS] is never reached here.
 */
const val FULL_BUFFER_MS = 50_000L

/** What the stage says while a title warms up: `Warming up · 0:12 of 0:50`. */
fun warmUpLine(aheadMs: Long): String =
    "Warming up · ${clockTime(aheadMs.coerceAtMost(FULL_BUFFER_MS))} of ${clockTime(FULL_BUFFER_MS)}"

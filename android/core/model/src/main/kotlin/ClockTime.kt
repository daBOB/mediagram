package model

/**
 * A position on a scrub bar, as a viewer reads it: `1:58:02` for a film,
 * `58:02` for an episode, `0:09` for the first few seconds.
 *
 * The one clock format every surface reads — catalogue resume lines and the
 * player's own transport alike — mirroring `clockTime` in the web player's
 * `format.js` down to dropping the hour when there isn't one and padding the
 * minutes only when there is. A viewer who uses more than one surface should
 * not have to read two clocks.
 *
 * Truncates rather than rounds, and treats anything that isn't a positive,
 * finite number of seconds as zero: a runtime the catalog does not yet know
 * and a length media3 reports as `C.TIME_UNSET` before it knows one are the
 * same "not yet" from here.
 */
fun clockTime(seconds: Double): String {
    val total = if (seconds.isFinite() && seconds > 0) seconds.toLong() else 0L
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val rest = (total % 60).toString().padStart(2, '0')
    return if (hours == 0L) "$minutes:$rest" else "$hours:${minutes.toString().padStart(2, '0')}:$rest"
}

package catalog

import model.Kind
import model.MediaSet
import model.Progress
import kotlin.math.roundToLong

/*
 * The two lines a set card says about a viewer's own place in it, ported
 * from `web/public/lib/format.js`'s `resumeLine`, `clockTime` and
 * `episodeLabel` — the web is authoritative, and these exist to agree with
 * it rather than redefine it.
 */

/**
 * Where someone got to in a title, as a share and a position: `42% · 12:30`.
 *
 * The share is for deciding — nearly done, or barely begun — and the
 * position is for recognising the moment playback stopped. A runtime this
 * device does not know gives the position alone, for the same reason
 * [data.ResumePoint.watchedFraction] refuses to place one: a percentage of
 * an unknown length is a number with nothing behind it.
 */
fun resumeLine(progress: Progress?): String {
    if (progress == null) return ""
    val at = progress.at
    if (!at.isFinite() || at < 0) return ""

    val runtime = progress.duration
    val measured = runtime != null && runtime.isFinite() && runtime > 0
    val share = if (measured) "${(minOf(1.0, at / runtime) * 100).roundToLong()}%" else null
    return listOfNotNull(share, clockTime(at)).joinToString(" · ")
}

/** A position on a scrub bar: `1:23`, or `1:23:45` once past an hour. */
fun clockTime(seconds: Double): String {
    val total = if (seconds.isFinite() && seconds > 0) seconds.toLong() else 0L
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val rest = (total % 60).toString().padStart(2, '0')
    return if (hours == 0L) "$minutes:$rest" else "$hours:${minutes.toString().padStart(2, '0')}:$rest"
}

/**
 * `S1E4` for an episode, `4` for a lesson, a `4-5` range for a set spanning
 * more than one, empty when unnumbered.
 */
fun episodeLabel(set: MediaSet): String {
    val first = set.episodeFirst ?: return ""
    val last = set.episodeLast
    val number = if (last != null && last != first) "$first-$last" else "$first"
    return if (set.kind == Kind.EPISODE && set.season != null) "S${set.season}E$number" else number
}

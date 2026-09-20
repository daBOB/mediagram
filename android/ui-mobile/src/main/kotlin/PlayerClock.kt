package ui

import java.util.Locale

/**
 * A position as a viewer reads it: `1:58:02` for a film, `58:02` for an
 * episode, `0:09` for the first few seconds.
 *
 * Mirrors `clockTime` in the web player's `format.js`, down to dropping the
 * hour when there isn't one and padding the minutes only when there is. Two
 * surfaces over one library should not print a time two ways — which is also
 * why the format is applied against `Locale.ROOT` rather than the device's:
 * a default locale that numbers in its own script would print a position in
 * digits the web player never uses.
 *
 * Truncates rather than rounds, and treats anything negative as zero: media3
 * reports a length it does not know yet as `C.TIME_UNSET`.
 */
internal fun clockTime(ms: Long): String {
    val total = if (ms > 0L) ms / 1_000L else 0L
    val hours = total / 3_600L
    val minutes = (total % 3_600L) / 60L
    val seconds = total % 60L
    return if (hours == 0L) {
        "%d:%02d".format(Locale.ROOT, minutes, seconds)
    } else {
        "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
    }
}

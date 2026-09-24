package playback

import java.util.Locale

/** How far one nudge moves the subtitle clock — ported from the web's `NUDGE_SECONDS` (`subtitle-panel.js`). */
const val CUE_OFFSET_STEP_SECONDS: Double = 0.1

/** As far as a subtitle file is ever wrong by. Beyond this it is the wrong file — ported from the web's `FURTHEST`. */
const val CUE_OFFSET_LIMIT_SECONDS: Double = 30.0

/** [seconds] clamped to +/-[CUE_OFFSET_LIMIT_SECONDS]; nought for anything that is not a finite number. */
fun clampCueOffsetSeconds(seconds: Double): Double {
    if (!seconds.isFinite()) return 0.0
    return seconds.coerceIn(-CUE_OFFSET_LIMIT_SECONDS, CUE_OFFSET_LIMIT_SECONDS)
}

/**
 * [current] moved by [steps] steps of [CUE_OFFSET_STEP_SECONDS], then
 * clamped. Rounded to a tenth first — floating point turns four taps of 0.1
 * into 0.30000000000000004, and the readout would say so.
 */
fun nudgeCueOffsetSeconds(current: Double, steps: Int): Double {
    val stepped = current + steps * CUE_OFFSET_STEP_SECONDS
    return clampCueOffsetSeconds(Math.round(stepped * 10.0) / 10.0)
}

/** The stored value, or nought when nothing is stored or it does not parse as a number. */
fun cueOffsetSecondsOrDefault(stored: String?): Double = clampCueOffsetSeconds(stored?.toDoubleOrNull() ?: 0.0)

/**
 * `+0.3s`, `-1.0s`, `0.0s` — signed on purpose, so a nudge past nought is
 * visibly a different instruction from one that never left it. `Locale.ROOT`
 * throughout: a decimal comma from the device's own locale would make this
 * unparseable by [cueOffsetSecondsOrDefault], which always reads a plain dot.
 */
fun cueOffsetLabel(seconds: Double): String {
    val sign = if (seconds > 0) "+" else ""
    return "$sign${String.format(Locale.ROOT, "%.1f", seconds)}s"
}

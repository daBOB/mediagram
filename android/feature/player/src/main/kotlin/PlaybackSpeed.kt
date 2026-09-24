package player

/**
 * The speeds worth offering — a port of the web's `SPEEDS`
 * (`transport.js`). Anything finer is a setting, not a choice.
 */
val PLAYBACK_SPEEDS: List<Float> = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

/** `1×`, `1.5×` — never `1.00×`, which is a measurement, not a speed. */
fun speedLabel(rate: Float): String {
    if (!rate.isFinite() || rate <= 0f) return "1×"
    return "${trimmedNumber(rate)}×"
}

/** The string a remembered speed is stored as — the web's own value, e.g. `"1.5"`. */
fun speedPreferenceValue(rate: Float): String = trimmedNumber(rate)

/**
 * The remembered value, or the default when it names none of
 * [PLAYBACK_SPEEDS] — a port of the web's `recallSpeed` (`transport.js`).
 * Never trusts an arbitrary stored number: the six speeds are every value
 * the sheet can write, so anything else is a stale or foreign one.
 */
fun speedOrDefault(stored: String?): Float {
    val asked = stored?.toFloatOrNull()
    return PLAYBACK_SPEEDS.firstOrNull { it == asked } ?: 1f
}

/** `1.5` prints as `1.5`, `1` prints as `1` — never `1.0`. */
private fun trimmedNumber(value: Float): String =
    if (value == value.toLong().toFloat()) value.toLong().toString() else value.toString()

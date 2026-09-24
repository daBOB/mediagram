package player

private val UNITS = listOf("B", "KB", "MB", "GB", "TB")

/** One decimal place without the host locale's comma: `7.0`, never `7,0`. */
private fun oneDecimal(value: Double): String {
    val tenths = Math.round(value * 10)
    return "${tenths / 10}.${tenths % 10}"
}

/**
 * A byte count, at one decimal place only where that changes the meaning.
 * Mirrors `format.js`'s `humanSize` — and `system.humanSize`, this module's
 * own copy of the same spelling. Feature modules do not depend on one
 * another, so the byte formatter every screen shows in the same words is
 * kept once per module that needs it rather than routed through a
 * feature-to-feature dependency for a handful of lines of arithmetic.
 */
internal fun humanSize(bytes: Long): String {
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < UNITS.size - 1) {
        value /= 1024
        unit += 1
    }
    val rounded = if (value < 10 && unit > 0) oneDecimal(value) else Math.round(value).toString()
    return "$rounded ${UNITS[unit]}"
}

package model

private val UNITS = listOf("B", "KB", "MB", "GB", "TB")

/** One decimal place without the host locale's comma: `7.0`, never `7,0`. */
private fun oneDecimal(value: Double): String {
    val tenths = Math.round(value * 10)
    return "${tenths / 10}.${tenths % 10}"
}

/** A byte count, at one decimal place only where that changes the meaning. Mirrors `format.js`'s `humanSize`. */
fun humanSize(bytes: Long): String {
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < UNITS.size - 1) {
        value /= 1024
        unit += 1
    }
    val rounded = if (value < 10 && unit > 0) oneDecimal(value) else Math.round(value).toString()
    return "$rounded ${UNITS[unit]}"
}

/**
 * What a cache holds against its ceiling: `1.0 GB of 2.0 GB (50%)`, or
 * `nothing yet of 2.0 GB` while it is empty — this device's own budget or a
 * LAN cache server's.
 *
 * [humanSize], the same spelling every other byte count on this screen and
 * in the playback overlay uses.
 */
fun heldOfBudget(
    held: Long,
    budget: Long,
): String {
    val budgetText = humanSize(budget)
    if (held == 0L) return "nothing yet of $budgetText"
    val percent = Math.round(held * 100.0 / budget)
    return "${humanSize(held)} of $budgetText ($percent%)"
}

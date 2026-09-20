package ui

/**
 * How the System screen turns raw counters into words. Mirrors the web
 * player's status panel (`status-lines.js`): a row whose value is not
 * known is left out entirely rather than shown blank, because a blank row
 * reads as a broken value rather than an absent one.
 *
 * All pure, all `internal`, none composable — [SystemScreen] is where
 * these sentences meet the layout.
 */

private val UNITS = listOf("B", "KB", "MB", "GB", "TB")

/** One decimal place without the host locale's comma: `7.0`, never `7,0`. */
private fun oneDecimal(value: Double): String {
    val tenths = Math.round(value * 10)
    return "${tenths / 10}.${tenths % 10}"
}

/** A byte count, at one decimal place only where that changes the meaning. Mirrors `format.js`'s `humanSize`. */
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

/**
 * Bytes, always to one decimal once past the plain-byte unit — unlike
 * [humanSize], which drops the decimal at ten and above. The Held row below
 * is read against its budget from one reading to the next, and a figure
 * that quietly lost its decimal at "10.0 GB" would look like it had
 * changed precision rather than crossed a threshold that means nothing
 * here.
 */
private fun sizeAlwaysOneDecimal(bytes: Long): String {
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < UNITS.size - 1) {
        value /= 1024
        unit += 1
    }
    val rounded = if (unit > 0) oneDecimal(value) else Math.round(value).toString()
    return "$rounded ${UNITS[unit]}"
}

/** What the cache holds against its ceiling: `7.0 GB of 20.0 GB (35%)`, or `nothing yet of 2.0 GB` while it is empty. */
internal fun heldOfBudget(held: Long, budget: Long): String {
    val budgetText = sizeAlwaysOneDecimal(budget)
    if (held == 0L) return "nothing yet of $budgetText"
    val percent = Math.round(held * 100.0 / budget)
    return "${sizeAlwaysOneDecimal(held)} of $budgetText ($percent%)"
}

/** How the reads went: the share served from disk, and the counts behind it. */
internal fun cacheReadsLine(fromCache: Long, fromUpstream: Long, hits: Int, misses: Int): String {
    val total = fromCache + fromUpstream
    if (total == 0L) return "nothing read yet"
    val percent = Math.round(fromCache * 100.0 / total)
    return "$percent% from disk ($hits hits, $misses misses)"
}

/** Whether this device's Telegram session is up, or `null` when the question does not apply. */
internal fun telegramLine(connected: Boolean?): String? = when (connected) {
    null -> null
    true -> "connected"
    false -> "disconnected"
}

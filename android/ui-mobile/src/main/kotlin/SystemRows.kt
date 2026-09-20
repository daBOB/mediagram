package ui

import data.RefreshOutcome

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

/**
 * How the reads went: the share of bytes served off the disk, and the round
 * trips to Telegram behind the rest.
 *
 * Not hits and misses, which is what the web player's line says. Its cache
 * counts both; nothing here does. `CacheDataSource.Factory` is built with no
 * `EventListener`, so the only counts this app has are bytes served from
 * disk, bytes fetched, and the fetches that carried them — and a round trip
 * that returned bytes is not a cache miss, it is what a miss costs.
 *
 * The reads that raised are the Upstream block's own row and are not said
 * again here under a second name: one number, on one screen, twice, is how a
 * person diagnosing something arrives at two different conclusions.
 */
internal fun cacheReadsLine(fromCache: Long, fromUpstream: Long, fetches: Int): String {
    val total = fromCache + fromUpstream
    if (total == 0L) return "nothing read yet"
    val percent = Math.round(fromCache * 100.0 / total)
    val trips = if (fetches == 1) "1 fetch" else "$fetches fetches"
    return "$percent% from disk ($trips upstream)"
}

/** Whether this device's Telegram session is up, or `null` when the question does not apply. */
internal fun telegramLine(connected: Boolean?): String? = when (connected) {
    null -> null
    true -> "connected"
    false -> "disconnected"
}

/** A day, in milliseconds. */
private const val DAY_MS = 86_400_000L

/**
 * How old the installed catalogue is, in the words someone would use.
 *
 * Deliberately coarse, as the web player's `catalogueAge` is: the useful
 * distinction is "current" against "this stopped refreshing a while ago",
 * and an exact timestamp invites arithmetic to answer a question that is
 * really yes-or-no. Its thresholds, not new ones.
 */
private fun catalogueAge(publishedAt: Long?, now: Long): String? {
    if (publishedAt == null) return null
    // Floored rather than truncated, so a catalogue dated a few hours into
    // the future lands below zero rather than on "today".
    val days = Math.floorDiv(now - publishedAt, DAY_MS)
    return when {
        // A clock that disagrees with the publisher's is likelier than a
        // catalogue from the future, and "published in -2 days" helps nobody.
        days < 0 -> "published just now"
        days == 0L -> "published today"
        days == 1L -> "published yesterday"
        days < 14 -> "published $days days ago"
        days < 60 -> "published ${days / 7} weeks ago"
        else -> "published ${days / 30} months ago"
    }
}

/**
 * How old the catalogue is and what the last attempt to replace it did, or
 * `null` when there is neither.
 *
 * The refusal is the one reading on this screen that is a warning: a
 * catalogue that could not be replaced looks exactly like a current one,
 * and nothing else here would say otherwise.
 *
 * The web player's version of this answers "read from this machine"
 * whenever its index did not arrive in a published package, because an
 * index built where it is served has no publisher to be older than. This
 * app has no such case — every catalogue it holds was pushed to a Telegram
 * channel and pulled back down — so that branch would print something false
 * on every phone. It is left out, and the age leads instead.
 *
 * [now] is a parameter with no default for the reason the web's is: a
 * function that reads the clock itself cannot be tested against one.
 */
internal fun refreshLine(publishedAt: Long?, outcome: RefreshOutcome?, now: Long): String? {
    val age = catalogueAge(publishedAt, now)
    if (outcome is RefreshOutcome.Refused) {
        val serving = if (age == null) "" else ", still serving the one $age"
        return "refresh refused — ${outcome.reason}$serving"
    }
    val did = when (outcome) {
        RefreshOutcome.Updated -> "refreshed just now"
        RefreshOutcome.AlreadyCurrent -> "already current"
        else -> null
    }
    return listOfNotNull(age, did).joinToString(" · ").ifEmpty { null }
}

/** How long this process has been up, coarsely: `2h 14m`, or just `14m` under an hour. */
internal fun uptimeLine(seconds: Long?): String? {
    if (seconds == null || seconds < 0) return null
    val days = seconds / 86_400
    val hours = (seconds % 86_400) / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

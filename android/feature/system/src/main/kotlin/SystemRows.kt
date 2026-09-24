package system

import data.RefreshOutcome
import model.heldOfBudget
import model.humanSize

/*
 * How the System screen turns raw counters into words. Mirrors the web
 * player's status panel (`status-lines.js`): a row whose value is not
 * known is left out entirely rather than shown blank, because a blank row
 * reads as a broken value rather than an absent one.
 *
 * All pure, none composable — the system screen is where these sentences
 * meet the layout.
 */

/**
 * How the reads went: the share of bytes served off the disk, and the round
 * trips to Telegram behind the rest.
 *
 * Not hits and misses, which is what the web player's line says. Its cache
 * counts both; nothing here does. A `CacheDataSource.EventListener` is
 * attached, but it answers in bytes: `onCachedBytesRead` and
 * `onCacheIgnored` are the whole interface, and neither one counts a read as
 * a hit or a miss. So the only counts this app has are bytes served from
 * disk, bytes fetched, and the fetches that carried them — and a round trip
 * that returned bytes is not a cache miss, it is what a miss costs.
 *
 * The reads that raised are the Upstream block's own row and are not said
 * again here under a second name: one number, on one screen, twice, is how a
 * person diagnosing something arrives at two different conclusions.
 */
fun cacheReadsLine(
    fromCache: Long,
    fromUpstream: Long,
    fetches: Int,
): String {
    val total = fromCache + fromUpstream
    if (total == 0L) return "nothing read yet"
    val percent = Math.round(fromCache * 100.0 / total)
    val trips = if (fetches == 1) "1 fetch" else "$fetches fetches"
    return "$percent% from disk ($trips upstream)"
}

/** Whether this device's Telegram session is up, or `null` when the question does not apply. */
fun telegramLine(connected: Boolean?): String? =
    when (connected) {
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
private fun catalogueAge(
    publishedAt: Long?,
    now: Long,
): String? {
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
fun refreshLine(
    publishedAt: Long?,
    outcome: RefreshOutcome?,
    now: Long,
): String? {
    val age = catalogueAge(publishedAt, now)
    if (outcome is RefreshOutcome.Refused) {
        val serving = if (age == null) "" else ", still serving the one $age"
        return "refresh refused — ${outcome.reason}$serving"
    }
    val did =
        when (outcome) {
            RefreshOutcome.Updated -> "refreshed just now"
            RefreshOutcome.AlreadyCurrent -> "already current"
            else -> null
        }
    return listOfNotNull(age, did).joinToString(" · ").ifEmpty { null }
}

/** How long this process has been up, coarsely: `2h 14m`, or just `14m` under an hour. */
fun uptimeLine(seconds: Long?): String? {
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

/**
 * What the Cache block says, as label-and-value pairs.
 *
 * Pure so a test pins the counters this screen hands over and not only the
 * sentence they are handed to. For as long as the sentence alone was
 * tested, these rows called round trips to Telegram "hits" and reads that
 * raised "misses", and printed the second of those twice on one screen
 * under two names, with nothing able to see it.
 */
fun cacheRows(state: SystemUiState): List<Pair<String, String?>> =
    listOf(
        "Held" to heldOfBudget(state.heldBytes, state.budgetBytes),
        "Reads" to cacheReadsLine(state.fromCacheBytes, state.fromUpstreamBytes, state.fetches),
    )

/** What the Upstream block says. Pure for the reason [cacheRows] is, and pinned by the same test. */
fun upstreamRows(state: SystemUiState): List<Pair<String, String?>> =
    listOf(
        "Since starting" to humanSize(state.fromUpstreamBytes),
        "Failed reads" to if (state.failedReads > 0) "${state.failedReads}" else "none",
    )

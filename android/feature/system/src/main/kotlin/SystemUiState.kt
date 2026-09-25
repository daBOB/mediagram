package system

import data.RefreshOutcome

/**
 * What the System screen has to say, read straight off [data.CoreClient]'s
 * catalog facts, [playback.CacheProvider]'s occupancy, and
 * [playback.PlaybackCounters]'s totals.
 *
 * Left unformatted on purpose: turning these into the sentences a viewer
 * reads is ui-mobile's `SystemRows`, which the composable in that module
 * calls. This module holds the facts, not their wording.
 */
data class SystemUiState(
    val origin: String,
    val sets: Long,
    val posters: Long,
    val schema: Int,
    /**
     * Milliseconds since the epoch when the installed catalogue was pushed,
     * or `null` when nothing is installed. Milliseconds rather than the
     * seconds the core reports, because the only thing done with it is to
     * subtract it from a wall clock, and the two have to agree on a unit.
     */
    val publishedAt: Long?,
    /** What the last refresh this process made did, or `null` before one is made. */
    val lastRefresh: RefreshOutcome?,
    val heldBytes: Long,
    val budgetBytes: Long,
    /** Which volume the cache actually opened on, and whether that was a fallback from what was chosen. */
    val volumeLabel: String,
    val fellBack: Boolean,
    val fromCacheBytes: Long,
    val fromUpstreamBytes: Long,
    val fetches: Int,
    val failedReads: Int,
    val connected: Boolean?,
    val versionName: String?,
    val uptimeSeconds: Long,
    /** Where the most recent chunk actually came from, or `null` before this process has read one. */
    val lastReadWasLan: Boolean? = null,
    /** The LAN server's host, when [lastReadWasLan] is `true`. */
    val lanHost: String? = null,
)

package system

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
    val heldBytes: Long,
    val budgetBytes: Long,
    val fromCacheBytes: Long,
    val fromUpstreamBytes: Long,
    val fetches: Int,
    val failedReads: Int,
    val connected: Boolean?,
    val versionName: String?,
    val uptimeSeconds: Long,
)

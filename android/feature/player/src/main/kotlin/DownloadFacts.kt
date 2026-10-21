package player

import kotlinx.coroutines.flow.StateFlow
import model.MediaSet
import playback.HeldSetsQuery
import playback.SeriesPreloading
import playback.TrafficSnapshot
import playback.UpstreamTraffic

/** What the statistics overlay says about downloading: read when it asks, never kept. */
data class DownloadFacts(
    val traffic: TrafficSnapshot = TrafficSnapshot(),
    /** How much of the open title is on this device, against its whole size. */
    val onDiskBytes: Long = 0,
    val totalBytes: Long = 0,
    val next: NextDownload? = null,
)

/** The episode the series preload is taking in behind the open one. */
data class NextDownload(
    val title: String,
    val heldBytes: Long,
    val totalBytes: Long,
)

/**
 * Reads [DownloadFacts] fresh on every call: the traffic as it is now, and
 * what the disk cache holds of the open title and of the one preloading.
 * Both sizes come from the cache's own index, the same answer the shelf's
 * "held" badge is built on, so the overlay and the badge cannot disagree.
 */
fun downloadFacts(
    openSet: StateFlow<MediaSet?>,
    seriesPreloader: SeriesPreloading,
    heldSets: HeldSetsQuery,
    traffic: UpstreamTraffic,
): suspend () -> DownloadFacts =
    {
        val open = openSet.value
        // The title open is the player's own download, not the next one.
        val next = seriesPreloader.downloading.value?.takeIf { it.setId != open?.setId }
        DownloadFacts(
            traffic = traffic.snapshot(),
            onDiskBytes = open?.let { heldSets.heldBytes(it.setId, it.totalBytes) } ?: 0,
            totalBytes = open?.totalBytes ?: 0,
            next = next?.let { NextDownload(it.title, heldSets.heldBytes(it.setId, it.totalBytes), it.totalBytes) },
        )
    }

package playback

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** What the byte path has done, for the surfaces that report it. */
data class PlaybackTotals(
    val fromCacheBytes: Long,
    val fromUpstreamBytes: Long,
    val fetches: Int,
    val failedReads: Int,
)

/**
 * Counts reads for the System screen and the playback overlay.
 *
 * Atomic rather than plain fields because they are written on ExoPlayer's
 * loader thread and read on the main one, and a long is not written
 * atomically on every device this runs on.
 *
 * Process-lifetime, like the player and the cache it counts for. A counter
 * reset per title would answer "what did this film cost", which is a
 * different and narrower question than the one either surface asks.
 */
class PlaybackCounters {

    private val fromCache = AtomicLong()
    private val fromUpstream = AtomicLong()
    private val fetchCount = AtomicInteger()
    private val failures = AtomicInteger()

    fun totals(): PlaybackTotals = PlaybackTotals(
        fromCacheBytes = fromCache.get(),
        fromUpstreamBytes = fromUpstream.get(),
        fetches = fetchCount.get(),
        failedReads = failures.get(),
    )

    /** One round trip to Telegram that returned bytes. */
    fun fetched(bytes: Int) {
        fetchCount.incrementAndGet()
        fromUpstream.addAndGet(bytes.toLong())
    }

    /** Bytes media3 served from its own disk cache, which never reached the core. */
    fun servedFromCache(bytes: Long) {
        fromCache.addAndGet(bytes)
    }

    /** A read that raised rather than returning. It brought no bytes. */
    fun readFailed() {
        failures.incrementAndGet()
    }
}

package playback

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Where the most recent chunk actually came from — the System screen's "Source" row. */
enum class ReadSource { TELEGRAM, LAN }

/** [ReadSource.LAN] carries the server's [host] it was read from; [ReadSource.TELEGRAM] carries none. */
data class LastRead(val source: ReadSource, val host: String?)

/** What the byte path has done, for the surfaces that report it. */
data class PlaybackTotals(
    val fromCacheBytes: Long,
    val fromUpstreamBytes: Long,
    val fetches: Int,
    val failedReads: Int,
    val lanHits: Int = 0,
    val lanMisses: Int = 0,
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
    private val lanHitCount = AtomicInteger()
    private val lanMissCount = AtomicInteger()
    private val lastRead = AtomicReference<LastRead?>()

    fun totals(): PlaybackTotals =
        PlaybackTotals(
            fromCacheBytes = fromCache.get(),
            fromUpstreamBytes = fromUpstream.get(),
            fetches = fetchCount.get(),
            failedReads = failures.get(),
            lanHits = lanHitCount.get(),
            lanMisses = lanMissCount.get(),
        )

    /** One round trip to Telegram that returned bytes. */
    fun fetched(bytes: Int) {
        fetchCount.incrementAndGet()
        fromUpstream.addAndGet(bytes.toLong())
        lastRead.set(LastRead(ReadSource.TELEGRAM, host = null))
    }

    /** Bytes media3 served from its own disk cache, which never reached the core. */
    fun servedFromCache(bytes: Long) {
        fromCache.addAndGet(bytes)
    }

    /** A read that raised rather than returning. It brought no bytes. */
    fun readFailed() {
        failures.incrementAndGet()
    }

    /** A chunk served from the LAN server at [host], without ever reaching Telegram. */
    fun lanHit(host: String) {
        lanHitCount.incrementAndGet()
        lastRead.set(LastRead(ReadSource.LAN, host))
    }

    /** The LAN server was asked and did not have the chunk; Telegram serves it instead. */
    fun lanMiss() {
        lanMissCount.incrementAndGet()
    }

    /** Where the most recent chunk came from, or `null` before this process has read one. */
    fun lastRead(): LastRead? = lastRead.get()
}

package playback

import android.util.Log
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap

/**
 * The most chunk fetches one read session keeps in flight at once.
 *
 * One fetch is one ~0.5 s Telegram round trip for one [CHUNK_BYTES] chunk —
 * about 16 Mbit/s however fast the link is, measured on the TV box at 15–17.
 * Four in flight is about 64 Mbit/s, above the 40–65 Mbit/s peaks the
 * heaviest 4K films reach. It is also grammers' own choice for a big
 * download (`download_media` runs four workers over the one connection it
 * holds per datacentre), so it asks no more of Telegram than the library
 * itself already does.
 */
const val MAX_READ_AHEAD = 4

/** What one fetch at a time delivers: 1 MiB per ~0.5 s round trip, rounded down. */
private const val BITS_PER_SECOND_PER_FETCH = 16_000_000L

/**
 * How much faster than the film plays its chunks should arrive. Twice: an
 * average hides scenes that run at double it, and a buffer that fills only
 * as fast as it drains never recovers from a seek.
 */
private const val HEADROOM = 2L

/** How long one failed fetch keeps every set to one fetch at a time. */
private const val BACKOFF_MS = 60_000L

private const val TAG = "readahead"

/**
 * How many fetches a set of [totalBytes] lasting [durationSecs] needs in
 * flight to arrive [HEADROOM] times faster than it plays, between 1 and
 * [MAX_READ_AHEAD].
 *
 * A set up to 8 Mbit/s — every episode and almost every 1080p film — stays
 * at one, exactly as it was read before there was a window: nothing it
 * plays is short, and every parallel request is one more Telegram could
 * push back on. One with no known duration stays at one too: a guess would
 * as likely widen a small file as narrow a large one.
 */
fun readAheadWidth(
    totalBytes: Long,
    durationSecs: Long?,
): Int {
    if (durationSecs == null || durationSecs <= 0 || totalBytes <= 0) return 1
    val bitsPerSecond = totalBytes * 8 / durationSecs
    val wanted = (bitsPerSecond * HEADROOM + BITS_PER_SECOND_PER_FETCH - 1) / BITS_PER_SECOND_PER_FETCH
    return wanted.coerceIn(1L, MAX_READ_AHEAD.toLong()).toInt()
}

/**
 * How wide a set's read-ahead window may be right now: [readAheadWidth] of
 * its own average bitrate, looked up once per set — or one, for
 * [backoffMs] after any chunk fetch fails.
 *
 * Any failure, not only a flood wait, because the core reports Telegram's
 * FLOOD_WAIT as the same network error a dropped connection gives, and
 * grammers has already slept through any wait of up to a minute before
 * Kotlin sees a failure at all. One that does arrive is a wait Telegram
 * meant for longer, or a link that is struggling: more requests in
 * parallel help neither. Reads carry on one at a time, as they always did,
 * and every further failure restarts the wait — the window opens again only
 * after a whole [backoffMs] with none.
 *
 * Shared by every session a factory opens, so a failure in one title's
 * window narrows the next title's too: the account Telegram pushed back on
 * is the same.
 */
class ReadAhead(
    private val durationSecs: suspend (setId: String) -> Long?,
    private val backoffMs: Long = BACKOFF_MS,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val widths = ConcurrentHashMap<String, Int>()

    @Volatile
    private var failedAt: Long? = null

    suspend fun width(
        setId: String,
        totalBytes: Long,
    ): Int {
        val base = widths[setId] ?: lookUp(setId, totalBytes)
        return if (backingOff()) 1 else base
    }

    /** A chunk fetch failed: one at a time until [backoffMs] passes without another. */
    fun failed() {
        if (!backingOff()) Log.w(TAG, "a chunk fetch failed; one at a time for ${backoffMs / 1000} s")
        failedAt = clock()
    }

    private fun backingOff(): Boolean {
        val at = failedAt ?: return false
        return clock() - at < backoffMs
    }

    /**
     * A lookup that fails costs the window, never the read, and is not
     * remembered: the next session asks again.
     */
    private suspend fun lookUp(
        setId: String,
        totalBytes: Long,
    ): Int {
        val duration =
            try {
                durationSecs(setId)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Log.w(TAG, "no duration for $setId: ${e.message}")
                return 1
            }
        return readAheadWidth(totalBytes, duration).also { width ->
            widths[setId] = width
            val mbps = duration?.takeIf { it > 0 }?.let { totalBytes * 8 / it / 1_000_000 }
            Log.i(TAG, "$setId: ${mbps ?: "?"} Mbit/s average, $width fetch(es) in flight")
        }
    }
}

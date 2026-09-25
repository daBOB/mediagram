package playback

import data.CoreClient
import uniffi.mediagram_core.CoreException
import java.io.IOException

/**
 * How much one chunk holds — the unit every `core.read` is aligned to, and
 * the stable key `(setId, index)` a memo, a future LAN peer, and every
 * device will agree on regardless of where a viewer happened to seek.
 *
 * Two of Telegram's own 512 KiB chunks, so a fetch keeps every byte it
 * pays for. Bigger is tempting and was tried: four megabytes took a 5.8 GB
 * film from a first frame in three seconds to one in seven, because the
 * first fetch of a set blocks for the whole of it and nothing can be
 * decoded until it lands. It bought no throughput in return: a player
 * buffers ahead and then reads at the speed the film plays, so the
 * transfer was never what was short.
 */
const val CHUNK_BYTES = 1 shl 20

/**
 * One fixed [CHUNK_BYTES] slice of a set, addressed by [index] rather than
 * a byte offset. [totalSize] is passed in rather than looked up again —
 * the caller already knows it from `open()`, and a chunk source has no
 * reason to ask twice.
 *
 * Throws [IOException] on failure, the same contract [MlibDataSource]
 * already gives ExoPlayer's loader.
 */
fun interface SetChunkSource {
    suspend fun chunk(
        setId: String,
        index: Long,
        totalSize: Long,
    ): ByteArray
}

/**
 * Reads one [CHUNK_BYTES] slice through [core], one round trip per chunk.
 * The last chunk of a set is shorter — [CHUNK_BYTES] would run past
 * [totalSize] — so its length is whatever is actually left; every other
 * chunk asks for the full, aligned amount.
 */
class TelegramChunkSource(
    private val core: CoreClient,
    private val counters: PlaybackCounters,
) : SetChunkSource {
    override suspend fun chunk(
        setId: String,
        index: Long,
        totalSize: Long,
    ): ByteArray {
        val offset = index * CHUNK_BYTES
        val want = minOf(CHUNK_BYTES.toLong(), totalSize - offset).toInt()
        return try {
            core.read(setId, offset, want).also { counters.fetched(it.size) }
        } catch (e: CoreException) {
            // Wrapped so ExoPlayer's Loader can retry an IOException (a
            // dropped Telegram connection, most likely) through its
            // LoadErrorHandlingPolicy instead of treating a plain
            // exception as an UnexpectedLoaderException and killing
            // playback outright.
            counters.readFailed()
            throw IOException("could not read from the set", e)
        }
    }
}

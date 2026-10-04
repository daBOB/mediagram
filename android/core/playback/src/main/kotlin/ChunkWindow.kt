package playback

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import java.io.IOException

/**
 * One read session's chunk fetches in flight ahead of its reader, so a set
 * that plays faster than one fetch at a time delivers is fetched several
 * chunks at once.
 *
 * Throughput here is bound by latency, not the link: a fetch is a ~0.5 s
 * round trip for one [CHUNK_BYTES] chunk, ~16 Mbit/s, and a 4K film averages
 * 27–31. `width` fetches in flight deliver `width` chunks per round trip —
 * grammers pipelines concurrent requests over the one connection it holds
 * per datacentre — so the window multiplies throughput without making any
 * one fetch, or the chunk itself, bigger (a bigger chunk was tried, and cost
 * the first frame four seconds).
 *
 * The first [take] fetches its chunk alone and opens the window only once
 * that chunk has arrived, so a first frame, and the first frame after a
 * seek, waits for one round trip as it always has, never for `width`.
 * `width` is asked alongside that first fetch rather than before it for the
 * same reason: it may be a catalog lookup.
 *
 * Every fetch goes through [chunks] — the shared [ChunkMemo] over the
 * LAN-first path — so a chunk fetched ahead is read from the LAN server
 * when it has it, mirrored to it when it does not, and never fetched twice
 * while it is in flight. A fetch [failed] reports narrows `width` for every
 * session; see [ReadAhead].
 *
 * Ends at [lastIndex], the last chunk this session's range reaches: a
 * `CacheDataSource` filling a gap asks only for the gap, and the chunks
 * after it are already on disk.
 *
 * [cancel] — the session closing, which is also how ExoPlayer seeks —
 * cancels whatever is still in flight; what already arrived stays in the
 * memo for the next session.
 *
 * Not thread-safe, and need not be: a session is read on one loader thread.
 * Only the fetches themselves run on [dispatcher].
 */
internal class ChunkWindow(
    private val setId: String,
    private val totalSize: Long,
    private val lastIndex: Long,
    private val chunks: SetChunkSource,
    private val width: suspend () -> Int,
    private val failed: () -> Unit,
    dispatcher: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val ahead = HashMap<Long, Deferred<ByteArray>>()
    private var opened = false

    /** Chunk [index], from the window when it is already on its way. */
    suspend fun take(index: Long): ByteArray {
        if (!opened) {
            return coroutineScope {
                val wanted = async { width() }
                val bytes = fetch(index)
                opened = true
                refill(index, wanted.await())
                bytes
            }
        }
        // Taken out before the refill, which retires everything up to index.
        val pending = ahead.remove(index)
        // Topped up before waiting, so `width` fetches are in flight while
        // the reader waits for the one it needs.
        refill(index, width())
        return pending?.await() ?: fetch(index)
    }

    /** Cancels every fetch still in flight. The window is not used again. */
    fun cancel() {
        scope.cancel()
        ahead.clear()
    }

    /** In flight afterwards: `index + 1` up to `index + width - 1`, and nothing else. */
    private fun refill(
        index: Long,
        width: Int,
    ) {
        val last = minOf(index + width - 1, lastIndex)
        ahead.keys.filter { it <= index || it > last }.forEach { ahead.remove(it)?.cancel() }
        for (next in index + 1..last) {
            if (next !in ahead) ahead[next] = scope.async { fetch(next) }
        }
    }

    private suspend fun fetch(index: Long): ByteArray =
        try {
            chunks.chunk(setId, index, totalSize)
        } catch (e: IOException) {
            failed()
            throw e
        }
}

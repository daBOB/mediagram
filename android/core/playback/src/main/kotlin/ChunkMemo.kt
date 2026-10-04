package playback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException

/**
 * The last [capacity] chunks [upstream] has fetched, keyed by `(setId,
 * index)`, and the ones it is fetching right now. A re-open inside a chunk
 * it already has costs no round trip; a second reader of a chunk already on
 * its way waits for that fetch instead of starting its own.
 *
 * Exists because `CacheDataSource` opens a new upstream read for every gap
 * it fills, and `MlibDataSource.open()` discards whatever it was holding —
 * a re-open landing inside the same chunk as a previous one would
 * otherwise re-download it whole. The default capacity is the four chunks
 * that needs — adjacent gaps land in the same or the next chunk, never four
 * away — plus a whole read-ahead window, so chunks a [ChunkWindow] fetched
 * ahead are still here when the next session re-opens on them.
 *
 * Fetches of different chunks run side by side: a read-ahead window asks
 * for several at once, and serialising them here would undo it. Fetches of
 * the same chunk never do. Duplicate fetches are how the web player once
 * fetched one 4 MiB run 47 times in 45 seconds and drew Telegram's flood
 * waits; here the first caller fetches and every later one awaits it.
 *
 * A fetcher's failure reaches its waiters — their own fetch would ask the
 * same question. Its cancellation does not: a window cancelled by a seek
 * says nothing about the chunk, so a waiter that is itself still wanted
 * fetches the chunk itself.
 *
 * [lock] guards only the maps, never a fetch, so it is a plain monitor:
 * nothing suspends while holding it.
 */
class ChunkMemo(
    private val upstream: SetChunkSource,
    private val capacity: Int = 4 + MAX_READ_AHEAD,
) : SetChunkSource {
    private val lock = Any()
    private val cache =
        object : LinkedHashMap<ChunkKey, ByteArray>(capacity, 1f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ChunkKey, ByteArray>) = size > capacity
        }
    private val inFlight = HashMap<ChunkKey, CompletableDeferred<ByteArray>>()

    override suspend fun chunk(
        setId: String,
        index: Long,
        totalSize: Long,
    ): ByteArray {
        val key = ChunkKey(setId, index)
        while (true) {
            var mine = false
            val shared =
                synchronized(lock) {
                    cache[key]?.let { return it }
                    inFlight.getOrPut(key) { CompletableDeferred<ByteArray>().also { mine = true } }
                }
            if (mine) return fetch(key, totalSize, shared)
            try {
                return shared.await()
            } catch (e: CancellationException) {
                // Our own cancellation ends here; the fetcher's sends us
                // round again to fetch the chunk ourselves.
                currentCoroutineContext().ensureActive()
            }
        }
    }

    private suspend fun fetch(
        key: ChunkKey,
        totalSize: Long,
        result: CompletableDeferred<ByteArray>,
    ): ByteArray {
        val outcome =
            runCatching {
                upstream.chunk(key.setId, key.index, totalSize).also { bytes ->
                    // Every offset MlibDataSource computes inside a chunk
                    // assumes the chunk is whole. A short one — a truncated
                    // network answer — would be served at the wrong offsets
                    // and, kept here, go on failing every retry; refused,
                    // the next read fetches it again.
                    val expected = expectedChunkLength(key.index, totalSize)
                    if (bytes.size != expected) {
                        throw IOException("chunk ${key.index} of ${key.setId} came back with ${bytes.size} of $expected bytes")
                    }
                }
            }
        synchronized(lock) {
            inFlight.remove(key)
            outcome.onSuccess { cache[key] = it }
        }
        outcome.fold(result::complete, result::completeExceptionally)
        return outcome.getOrThrow()
    }

    private data class ChunkKey(val setId: String, val index: Long)
}

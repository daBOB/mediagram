package playback

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The last [capacity] chunks [upstream] has fetched, keyed by `(setId,
 * index)`. A re-open inside a chunk it already has costs no round trip.
 *
 * Exists because `CacheDataSource` opens a new upstream read for every gap
 * it fills, and `MlibDataSource.open()` discards whatever it was holding —
 * a re-open landing inside the same chunk as a previous one would
 * otherwise re-download it whole. Four chunks (4 MiB) is generous for
 * that: adjacent gaps land in the same or the next chunk, never four away.
 *
 * [mutex] guards the whole lookup-or-fetch-and-store sequence, not just
 * the map: two data sources from the same factory can call [chunk]
 * concurrently on different loader threads, and without it two misses for
 * the same key would both reach [upstream] — the exact double fetch this
 * class exists to remove. Holding it across [upstream]'s own suspend call
 * serialises every fetch through one memo, which is the cost of that
 * guarantee; [upstream] is one Telegram connection per factory already, so
 * nothing above this was reading it in parallel to begin with.
 */
class ChunkMemo(
    private val upstream: SetChunkSource,
    private val capacity: Int = 4,
) : SetChunkSource {
    private val mutex = Mutex()
    private val cache =
        object : LinkedHashMap<ChunkKey, ByteArray>(capacity, 1f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ChunkKey, ByteArray>) = size > capacity
        }

    override suspend fun chunk(
        setId: String,
        index: Long,
        totalSize: Long,
    ): ByteArray =
        mutex.withLock {
            val key = ChunkKey(setId, index)
            cache[key] ?: upstream.chunk(setId, index, totalSize).also { cache[key] = it }
        }

    private data class ChunkKey(val setId: String, val index: Long)
}

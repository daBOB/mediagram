package playback

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.IOException

/** What [LanFirstChunkSource] hands a chunk it just fetched from Telegram, to mirror onto the LAN server. */
fun interface LanChunkWriter {
    fun enqueue(
        setId: String,
        index: Long,
        total: Long,
        bytes: ByteArray,
    )
}

private data class QueuedChunk(val setId: String, val index: Long, val total: Long, val bytes: ByteArray)

/**
 * Mirrors chunks Telegram just served onto the LAN server in the
 * background — best-effort sharing, never something a reader waits on.
 *
 * Bounded to [capacity] and drop-oldest: a queue that grew without limit
 * during a long binge would hold gigabytes a viewer has already watched
 * past by the time it got around to writing them, and a queue that blocked
 * the reader waiting for room would turn a slow write into a stalled read.
 * One dedicated worker, so writes to the same server never race each other
 * out of order for no reason; [enqueue] itself never suspends, so it costs
 * [LanFirstChunkSource]'s caller nothing to call.
 *
 * A 401 halts every write after it — [onUnauthorized] fires once, for
 * Settings to say the token was rejected — but never the chunk source: a
 * write failure of any kind stays entirely on this side of [enqueue].
 */
class LanWriteQueue(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    private val client: LanChunkProtocol,
    private val server: suspend () -> LanServer?,
    private val token: suspend () -> String?,
    private val onUnauthorized: () -> Unit,
    capacity: Int = CAPACITY,
) : LanChunkWriter {
    private val queue = Channel<QueuedChunk>(capacity, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    @Volatile
    private var halted = false

    init {
        scope.launch(dispatcher) {
            for (item in queue) {
                if (halted) continue
                write(item)
            }
        }
    }

    override fun enqueue(
        setId: String,
        index: Long,
        total: Long,
        bytes: ByteArray,
    ) {
        if (halted) return
        queue.trySend(QueuedChunk(setId, index, total, bytes))
    }

    private suspend fun write(item: QueuedChunk) {
        val srv = server() ?: return
        val tok = token() ?: return
        try {
            when (client.put(srv.baseUrl, tok, item.setId, item.index, item.total, item.bytes)) {
                LanPutResult.Unauthorized -> {
                    halted = true
                    onUnauthorized()
                }
                LanPutResult.Stored, LanPutResult.Rejected -> Unit
            }
        } catch (e: IOException) {
            // A LAN write that failed to even reach the server costs
            // nothing but the sharing it would have done — the chunk was
            // already served to the reader from Telegram before this ran.
        }
    }

    private companion object {
        const val CAPACITY = 8
    }
}

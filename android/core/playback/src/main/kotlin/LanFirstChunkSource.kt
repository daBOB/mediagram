package playback

import java.io.IOException

/** One reachable LAN cache server: [baseUrl] to read and write through, [host] for what the System screen shows. */
data class LanServer(val baseUrl: String, val host: String)

/**
 * Tries [lan] before [telegram]. Sits *under* [ChunkMemo] — a re-open
 * inside a chunk this process already fetched is served from the memo
 * without asking the LAN server again either — and *above* [telegram]'s own
 * [TelegramChunkSource], which every miss and every fallback runs through
 * exactly as it would with no LAN server at all.
 *
 * Skipped outright, at no cost beyond the one branch below, when [network]
 * reports a metered connection, [server] answers `null` (no server known,
 * or the feature is off), or the last attempt failed less than
 * [downWindowMs] ago — that window is what stops a server that just went
 * offline from being retried on every single chunk a title still playing
 * asks for. It is purely time-based: nothing clears it early, so a server
 * that comes back mid-window is not retried until the window itself lapses.
 *
 * A successful GET that comes back short — [LanChunkClient.get] already
 * folds a wrong length into the same `null` a 404 gives — is a miss, not a
 * failure: it does not mark the server down, and Telegram serves it exactly
 * as a 404 would.
 */
class LanFirstChunkSource(
    private val lan: LanChunkProtocol,
    private val telegram: SetChunkSource,
    private val network: UnmeteredNetworkCheck,
    private val server: suspend () -> LanServer?,
    private val writes: LanChunkWriter,
    private val counters: PlaybackCounters,
    private val downWindowMs: Long = DOWN_WINDOW_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) : SetChunkSource {
    @Volatile
    private var downSince: Long? = null

    override suspend fun chunk(
        setId: String,
        index: Long,
        totalSize: Long,
    ): ByteArray {
        val srv = server()
        if (srv != null && network.isUnmetered() && !isDown()) {
            try {
                val hit = lan.get(srv.baseUrl, setId, index, expectedChunkLength(index, totalSize))
                if (hit != null) {
                    counters.lanHit(srv.host)
                    return hit
                }
                counters.lanMiss()
            } catch (e: IOException) {
                downSince = clock()
            }
        }
        val bytes = telegram.chunk(setId, index, totalSize)
        writes.enqueue(setId, index, totalSize, bytes)
        return bytes
    }

    private fun isDown(): Boolean {
        val since = downSince ?: return false
        return clock() - since < downWindowMs
    }

    private companion object {
        const val DOWN_WINDOW_MS = 60_000L
    }
}

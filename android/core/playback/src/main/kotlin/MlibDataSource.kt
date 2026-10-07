// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import uniffi.mediagram_core.CoreException
import uniffi.mediagram_core.CoreInterface
import java.io.IOException

/**
 * The URI a set plays under: `mlib://set/<setId>`, opaque on purpose — no
 * `chat_id`, `message_id` or `doc_id` ever appears in it. Built through
 * [Uri.Builder] rather than string interpolation so a `setId` containing
 * characters that would otherwise break the URI is encoded correctly.
 */
fun setUri(setId: String): Uri =
    Uri
        .Builder()
        .scheme("mlib")
        .authority("set")
        .appendPath(setId)
        .build()

/**
 * Reads one set's bytes through [chunks] for ExoPlayer, in whole
 * [CHUNK_BYTES] chunks. [core] answers only a set's total size — the one thing
 * a chunk fetch needs before it can ask for anything — every actual byte
 * comes back through [chunks], which may serve one from a memo rather than
 * Telegram.
 *
 * [core] is nullable because there is a window — between signing a device
 * out and setting it up again — in which no core exists. A read session
 * opened then fails as an [IOException], which is a state ExoPlayer's
 * loader understands, rather than reading through whatever core happened to
 * be current when the player was built.
 *
 * [readAhead], when given, lets a session fetch several chunks at once
 * ([ChunkWindow]) for a set whose bitrate one fetch at a time cannot keep
 * up with; `null` reads one chunk at a time, on demand. [dispatcher] is
 * where those fetches run.
 */
class MlibDataSource(
    private val core: CoreInterface?,
    private val chunks: SetChunkSource,
    private val readAhead: ReadAhead? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BaseDataSource(true) {
    private var setId: String? = null
    private var position = 0L
    private var remaining = 0L
    private var total = 0L
    private var uri: Uri? = null

    // What one chunk fetch brought back, and how much of it has been
    // handed out. A fresh open() may land mid-chunk, so handedOut starts
    // wherever this session's position falls inside it, not always zero.
    private var held = EMPTY
    private var handedOut = 0
    private var window: ChunkWindow? = null

    @Suppress("DEPRECATION") // POSITION_OUT_OF_RANGE has no replacement constant; still the documented reason for this exact case.
    override fun open(dataSpec: DataSpec): Long {
        val client = core ?: throw IOException("this device is not set up to read the library")
        val id = dataSpec.uri.lastPathSegment ?: throw IOException("no set in the given URI")
        // Blocking, like fetch() below: ExoPlayer calls open() on its loader
        // thread and expects it to block until the size is known.
        val setTotal =
            try {
                runBlocking { client.totalSize(id).toLong() }
            } catch (e: CoreException) {
                throw IOException("could not read the set's size", e)
            }
        if (dataSpec.position > setTotal) {
            throw DataSourceException(DataSourceException.POSITION_OUT_OF_RANGE)
        }
        uri = dataSpec.uri
        setId = id
        total = setTotal
        position = dataSpec.position
        remaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) setTotal - position else dataSpec.length
        // Whatever was held belonged to the previous position; a seek lands
        // here and must not be served stale bytes.
        held = EMPTY
        handedOut = 0
        window?.cancel()
        window = windowFor(id, setTotal, lastIndex = (minOf(position + remaining, setTotal) - 1) / CHUNK_BYTES)
        transferStarted(dataSpec)
        return remaining
    }

    /**
     * Serves from what is already held, fetching a whole chunk only when it
     * runs out.
     *
     * ExoPlayer asks in buffer segments — 64 KiB at most — and a fetch that
     * reached Telegram costs a round trip to resolve the part plus a whole
     * [CHUNK_BYTES] chunk, most of which a 64 KiB answer would throw away.
     * Holding a whole chunk and handing it out a segment at
     * a time is what keeps a parser reading a few bytes at a time to one
     * round trip instead of hundreds.
     */
    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        if (handedOut == held.size) {
            if (position >= total) {
                // remaining is still > 0, so the DataSpec's own declared
                // length ran past the set's real end. A chunk fetch never
                // asks the core for an offset that is out of range, so
                // that is checked here instead of relying on whatever
                // bounds error the core would otherwise throw.
                throw IOException("the requested range runs past the end of the set")
            }
            val index = position / CHUNK_BYTES
            held = fetch(index)
            handedOut = (position - index * CHUNK_BYTES).toInt()
        }
        // A whole chunk may hold more than this DataSpec asked for — the
        // fetch above does not stop at `remaining`, only `held` does.
        val served = minOf(length.toLong(), (held.size - handedOut).toLong(), remaining).toInt()
        held.copyInto(buffer, offset, handedOut, handedOut + served)
        handedOut += served
        position += served
        remaining -= served
        bytesTransferred(served)
        return served
    }

    // Blocking is correct here: ExoPlayer calls read() on its loader
    // thread and expects it to block until bytes arrive or the input
    // ends. This and open() are the only places in :core:playback
    // runBlocking is allowed — everywhere else it would risk landing on
    // main.
    private fun fetch(index: Long): ByteArray = runBlocking { window!!.take(index) }

    private fun windowFor(
        id: String,
        setTotal: Long,
        lastIndex: Long,
    ): ChunkWindow =
        ChunkWindow(
            setId = id,
            totalSize = setTotal,
            lastIndex = lastIndex,
            chunks = chunks,
            width = { readAhead?.width(id, setTotal) ?: 1 },
            failed = { readAhead?.failed() },
            dispatcher = dispatcher,
        )

    override fun getUri(): Uri? = uri

    override fun close() {
        if (setId != null) {
            setId = null
            uri = null
            remaining = 0L
            held = EMPTY
            handedOut = 0
            // A seek closes before it re-opens: fetches for where the
            // reader was going are no longer wanted.
            window?.cancel()
            window = null
            transferEnded()
        }
    }

    private companion object {
        val EMPTY = ByteArray(0)
    }
}

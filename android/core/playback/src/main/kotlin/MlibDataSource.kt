package playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import data.CoreClient
import kotlinx.coroutines.runBlocking
import java.io.IOException

/**
 * The URI a set plays under: `mlib://set/<setId>`, opaque on purpose — no
 * `chat_id`, `message_id` or `doc_id` ever appears in it.
 */
fun setUri(setId: String): Uri = Uri.parse("mlib://set/$setId")

/**
 * Reads one set's bytes through [CoreClient] for ExoPlayer. Offset-to-part
 * mapping happens in the Rust core; this class only accounts for position
 * and remaining length across however many `read` calls that takes.
 */
class MlibDataSource(private val core: CoreClient) : BaseDataSource(true) {
    private var setId: String? = null
    private var position = 0L
    private var remaining = 0L
    private var uri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        val id = dataSpec.uri.lastPathSegment ?: throw IOException("no set in the given URI")
        setId = id
        position = dataSpec.position
        val total = core.totalSize(id)
        remaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) total - position else dataSpec.length
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val want = minOf(length.toLong(), remaining).toInt()
        // Blocking is correct here: ExoPlayer calls read() on its loader
        // thread and expects it to block until bytes arrive or the input
        // ends. This is the one place in :core:playback runBlocking is
        // allowed — everywhere else it would risk landing on main.
        val bytes = runBlocking { core.read(setId!!, position, want) }
        if (bytes.isEmpty()) return C.RESULT_END_OF_INPUT
        bytes.copyInto(buffer, offset)
        position += bytes.size
        remaining -= bytes.size
        bytesTransferred(bytes.size)
        return bytes.size
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        if (setId != null) {
            setId = null
            transferEnded()
        }
    }
}

/** Hands ExoPlayer a fresh [MlibDataSource] per read session. */
class MlibDataSourceFactory(private val core: CoreClient) : DataSource.Factory {
    override fun createDataSource(): DataSource = MlibDataSource(core)
}

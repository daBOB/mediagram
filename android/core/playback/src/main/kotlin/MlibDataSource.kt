// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import data.CoreClient
import kotlinx.coroutines.runBlocking
import uniffi.mediagram_core.CoreException
import java.io.IOException

/**
 * The URI a set plays under: `mlib://set/<setId>`, opaque on purpose — no
 * `chat_id`, `message_id` or `doc_id` ever appears in it. Built through
 * [Uri.Builder] rather than string interpolation so a `setId` containing
 * characters that would otherwise break the URI is encoded correctly.
 */
fun setUri(setId: String): Uri = Uri.Builder().scheme("mlib").authority("set").appendPath(setId).build()

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

    @Suppress("DEPRECATION") // POSITION_OUT_OF_RANGE has no replacement constant; still the documented reason for this exact case.
    override fun open(dataSpec: DataSpec): Long {
        val id = dataSpec.uri.lastPathSegment ?: throw IOException("no set in the given URI")
        val total = try {
            core.totalSize(id)
        } catch (e: CoreException) {
            throw IOException("could not read the set's size", e)
        }
        if (dataSpec.position > total) {
            throw DataSourceException(DataSourceException.POSITION_OUT_OF_RANGE)
        }
        uri = dataSpec.uri
        setId = id
        position = dataSpec.position
        remaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) total - position else dataSpec.length
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val want = minOf(length.toLong(), remaining).toInt()
        // Blocking is correct here: ExoPlayer calls read() on its loader
        // thread and expects it to block until bytes arrive or the input
        // ends. This is the one place in :core:playback runBlocking is
        // allowed — everywhere else it would risk landing on main.
        val bytes = try {
            runBlocking { core.read(setId!!, position, want) }
        } catch (e: CoreException) {
            // Wrapped so ExoPlayer's Loader can retry an IOException (a
            // dropped Telegram connection, most likely) through its
            // LoadErrorHandlingPolicy instead of treating a plain
            // exception as an UnexpectedLoaderException and killing
            // playback outright.
            throw IOException("could not read from the set", e)
        }
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
            uri = null
            remaining = 0L
            transferEnded()
        }
    }
}

/** Hands ExoPlayer a fresh [MlibDataSource] per read session. */
class MlibDataSourceFactory(private val core: CoreClient) : DataSource.Factory {
    override fun createDataSource(): DataSource = MlibDataSource(core)
}

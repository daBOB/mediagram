package playback

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** `GET /v1/status`'s body, for the Settings row that reports how much a paired server is holding. */
data class LanServerStatus(val heldBytes: Long, val budgetBytes: Long, val chunks: Long)

/** How a PUT to the LAN server came back. */
sealed interface LanPutResult {
    /** 201 (newly stored) or 200 (already held) — either way the server now has it. */
    data object Stored : LanPutResult

    /** The token this device holds does not match the server's; further writes must stop. */
    data object Unauthorized : LanPutResult

    /** A 400/409/413 — a shape or length the server refuses. Not a reason to stop writing altogether. */
    data object Rejected : LanPutResult
}

/**
 * The wire protocol [LanFirstChunkSource], [LanWriteQueue] and
 * [LanServerLocator] actually depend on — narrow enough that each of their
 * tests fakes it directly, rather than running a real HTTP server for
 * every branch. [LanChunkClient] is the one real implementation, and the
 * one place any of it is proven against an actual server
 * ([LanChunkClientTest]'s `MockWebServer`).
 */
interface LanChunkProtocol {
    suspend fun get(
        baseUrl: String,
        setId: String,
        index: Long,
        expectedLength: Int,
    ): ByteArray?

    suspend fun put(
        baseUrl: String,
        token: String,
        setId: String,
        index: Long,
        total: Long,
        body: ByteArray,
    ): LanPutResult

    suspend fun verify(baseUrl: String): Boolean

    /** `null` for anything but a clean 200 with a well-formed body — same leniency as [verify]. */
    suspend fun status(baseUrl: String): LanServerStatus?
}

/**
 * Speaks the `mediagram_cache` wire protocol (`docs/running-the-player.md`,
 * "Home cache server"): one GET per chunk read, one signed PUT per chunk
 * shared back. `HttpURLConnection` rather than a library — a LAN chunk is
 * a single request/response with no need for pooling or HTTP/2, and this
 * needs no new Gradle dependency.
 *
 * Timeouts are short on purpose: a LAN answers in milliseconds, and every
 * millisecond spent waiting here is a millisecond [LanFirstChunkSource]
 * holds off falling back to Telegram.
 */
class LanChunkClient(
    private val connectTimeoutMs: Int = 1_000,
    private val readTimeoutMs: Int = 5_000,
) : LanChunkProtocol {
    /**
     * `null` for a 404 (no such chunk yet) or a 200 whose body is not
     * exactly [expectedLength] — both are a miss, not a failure. Throws
     * [IOException] (including a connect or read timeout) for anything that
     * is not a clean HTTP answer, which [LanFirstChunkSource] treats as the
     * server going down.
     */
    override suspend fun get(
        baseUrl: String,
        setId: String,
        index: Long,
        expectedLength: Int,
    ): ByteArray? =
        withContext(Dispatchers.IO) {
            val connection = open("GET", "$baseUrl${chunkPath(setId, index)}")
            try {
                when (connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val body = connection.inputStream.use { it.readBytes() }
                        body.takeIf { it.size == expectedLength }
                    }
                    else -> null
                }
            } finally {
                connection.disconnect()
            }
        }

    /**
     * Signs and sends one chunk. [total] is the whole set's byte length,
     * carried in `X-Set-Total` — the server's own length rule needs it on
     * every chunk, not only the last.
     */
    override suspend fun put(
        baseUrl: String,
        token: String,
        setId: String,
        index: Long,
        total: Long,
        body: ByteArray,
    ): LanPutResult =
        withContext(Dispatchers.IO) {
            val path = chunkPath(setId, index)
            val connection = open("PUT", "$baseUrl$path")
            connection.doOutput = true
            connection.setRequestProperty("X-Set-Total", total.toString())
            connection.setRequestProperty("Authorization", "$LAN_AUTH_SCHEME ${sign(token, "PUT", path, total, body)}")
            try {
                connection.outputStream.use { it.write(body) }
                when (connection.responseCode) {
                    HttpURLConnection.HTTP_CREATED, HttpURLConnection.HTTP_OK -> LanPutResult.Stored
                    HttpURLConnection.HTTP_UNAUTHORIZED -> LanPutResult.Unauthorized
                    else -> LanPutResult.Rejected
                }
            } finally {
                connection.disconnect()
            }
        }

    /** `true` once `GET /v1/status` answers 200 — how [LanServerLocator] tells a live server from a dead address. */
    override suspend fun verify(baseUrl: String): Boolean =
        withContext(Dispatchers.IO) {
            val connection = open("GET", "$baseUrl/v1/status")
            try {
                connection.responseCode == HttpURLConnection.HTTP_OK
            } catch (e: IOException) {
                false
            } finally {
                connection.disconnect()
            }
        }

    override suspend fun status(baseUrl: String): LanServerStatus? =
        withContext(Dispatchers.IO) {
            val connection = open("GET", "$baseUrl/v1/status")
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                val body = connection.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
                statusFromJson(body)
            } catch (e: IOException) {
                null
            } finally {
                connection.disconnect()
            }
        }

    private fun open(
        method: String,
        url: String,
    ): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
        }

    private fun chunkPath(
        setId: String,
        index: Long,
    ): String = "/v1/sets/$setId/chunks/$index"
}

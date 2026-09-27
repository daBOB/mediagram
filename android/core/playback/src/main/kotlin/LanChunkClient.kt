package playback

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Speaks the `mediagram_cache` wire protocol (`docs/running-the-player.md`,
 * "Home cache server"): one GET per chunk read, one signed PUT per chunk
 * shared back. `HttpURLConnection` rather than a library — a LAN chunk is
 * a single request/response with no need for pooling or HTTP/2, and this
 * needs no new Gradle dependency.
 *
 * Every method builds its connection inside its own `try`: a manual
 * address a viewer typed without a scheme (`"192.168.1.5:7788"`, exactly
 * what `avahi-browse` prints) fails `URL(...)` with a `MalformedURLException`
 * — an [IOException] subtype — the moment a connection is opened, not
 * afterward, so it is caught by the same handling as a connect timeout
 * rather than escaping uncaught into [LanServerLocator]'s discovery
 * coroutine and crashing the process.
 *
 * Timeouts are short on purpose: a LAN answers in milliseconds, and every
 * millisecond spent waiting here is a millisecond [LanFirstChunkSource]
 * holds off falling back to Telegram. [get]'s own [getDeadlineMs] is what
 * stands in for tracking a moving median of read latency: a server that is
 * merely slow, not down, is indistinguishable from one that will never
 * answer once this deadline is the only thing watching it, and that is the
 * chosen trade — simplicity over a more precise "slow but alive" signal.
 */
class LanChunkClient(
    private val connectTimeoutMs: Int = 1_000,
    private val readTimeoutMs: Int = 5_000,
    private val getDeadlineMs: Long = 2_000,
) : LanChunkProtocol {
    /**
     * `null` for a 404 (no such chunk yet) or a 200 whose body does not
     * come to exactly [expectedLength] bytes — both are a miss, not a
     * failure. Throws [IOException] for a 5xx, a connect/read failure, or
     * the [getDeadlineMs] deadline itself — all of which
     * [LanFirstChunkSource] treats as the server going down.
     */
    override suspend fun get(
        baseUrl: String,
        setId: String,
        index: Long,
        expectedLength: Int,
    ): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                withTimeout(getDeadlineMs) {
                    withCancellableConnection({ open("GET", "$baseUrl${chunkPath(setId, index)}") }) { connection ->
                        when {
                            connection.responseCode == HttpURLConnection.HTTP_OK -> readExactly(connection, expectedLength)
                            connection.responseCode >= HttpURLConnection.HTTP_INTERNAL_ERROR ->
                                throw IOException("LAN GET failed with ${connection.responseCode}")
                            else -> null
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                throw IOException("LAN GET did not answer within ${getDeadlineMs}ms", e)
            }
        }

    /**
     * Signs and sends one chunk. [total] is the whole set's byte length,
     * carried in `X-Set-Total` — the server's own length rule needs it on
     * every chunk, not only the last. `setFixedLengthStreamingMode` rather
     * than the default buffering mode: [body] is already fully in memory
     * (a 1 MiB chunk at most), so there is no reason to buffer it a second
     * time inside the connection before it goes out.
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
            withCancellableConnection({ open("PUT", "$baseUrl$path") }) { connection ->
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                connection.setRequestProperty("X-Set-Total", total.toString())
                connection.setRequestProperty(
                    "Authorization",
                    "$LAN_AUTH_SCHEME ${sign(token, "PUT", path, total, body)}",
                )
                connection.outputStream.use { it.write(body) }
                when (connection.responseCode) {
                    HttpURLConnection.HTTP_CREATED, HttpURLConnection.HTTP_OK -> LanPutResult.Stored
                    HttpURLConnection.HTTP_UNAUTHORIZED -> LanPutResult.Unauthorized
                    else -> LanPutResult.Rejected
                }
            }
        }

    /** `true` once `GET /v1/status` answers 200 — how [LanServerLocator] tells a live server from a dead address. */
    override suspend fun verify(baseUrl: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                withCancellableConnection({ open("GET", "$baseUrl/v1/status") }) {
                    it.responseCode == HttpURLConnection.HTTP_OK
                }
            } catch (e: IOException) {
                false
            }
        }

    override suspend fun status(baseUrl: String): LanServerStatus? = getJson("$baseUrl/v1/status", ::statusFromJson)

    override suspend fun setStatus(
        baseUrl: String,
        setId: String,
    ): LanSetStatus? = getJson("$baseUrl${setStatusPath(setId)}", ::setStatusFromJson)

    /**
     * One GET, parsed by [parse] on a clean 200 — the shared shape behind
     * [status] and [setStatus]: `null` for anything else (a non-200, a
     * connect/read failure, or a body [parse] itself refuses), never a
     * thrown [IOException]. A real status body is well under a hundred
     * bytes; anything past [STATUS_LIMIT_BYTES] is not one, and is not
     * read into memory.
     */
    private suspend fun <T> getJson(
        url: String,
        parse: (String) -> T?,
    ): T? =
        withContext(Dispatchers.IO) {
            try {
                withCancellableConnection({ open("GET", url) }) { connection ->
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        null
                    } else {
                        readAtMost(connection, STATUS_LIMIT_BYTES)?.let { parse(it.toString(Charsets.UTF_8)) }
                    }
                }
            } catch (e: IOException) {
                null
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

    private fun setStatusPath(setId: String): String = "/v1/sets/$setId"
}

private const val STATUS_LIMIT_BYTES = 4 * 1024

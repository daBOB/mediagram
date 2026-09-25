package playback

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import java.net.HttpURLConnection

/**
 * Runs [block] against a connection [open] builds, disconnecting it the
 * moment this coroutine is cancelled. A plain `HttpURLConnection` blocks a
 * real thread and does not notice coroutine cancellation on its own — left
 * alone, a server that stops answering mid-response would keep an
 * ExoPlayer cancel or seek, or [LanChunkClient.get]'s own deadline, stuck
 * behind whichever call still holds [ChunkMemo]'s mutex waiting for it.
 *
 * `invokeOnCompletion(onCancelling = true, ...)` — [InternalCoroutinesApi],
 * used deliberately — is what makes this actually work: the public,
 * stable overload only fires once a job reaches its *final* state, which a
 * job blocked in synchronous, non-suspending code (every call in this
 * file) never reaches on its own until that call returns. `onCancelling`
 * fires the moment cancellation is *requested*, while the blocking read is
 * still in progress, which is the one moment `disconnect()` actually needs
 * to run to unblock it.
 */
@OptIn(InternalCoroutinesApi::class)
internal suspend fun <T> withCancellableConnection(
    open: () -> HttpURLConnection,
    block: (HttpURLConnection) -> T,
): T {
    var connection: HttpURLConnection? = null
    val handle =
        currentCoroutineContext()[Job]?.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { cause ->
            if (cause != null) connection?.disconnect()
        }
    try {
        connection = open()
        return block(connection)
    } finally {
        handle?.dispose()
        connection?.disconnect()
    }
}

/**
 * Reads exactly [expectedLength] bytes from [connection]'s body, or `null`
 * for anything else — a declared `Content-Length` that disagrees, a body
 * that ends early, or one that keeps going past it.
 *
 * Never allocates more than [expectedLength] bytes regardless of what the
 * server actually sends: a spoofed responder streaming an unbounded body
 * must not be able to exhaust the heap the way a plain
 * `InputStream.readBytes()` would let it.
 */
internal fun readExactly(
    connection: HttpURLConnection,
    expectedLength: Int,
): ByteArray? {
    val declared = connection.contentLengthLong
    if (declared >= 0 && declared != expectedLength.toLong()) return null
    return connection.inputStream.use { stream ->
        val buffer = ByteArray(expectedLength)
        var read = 0
        while (read < expectedLength) {
            val n = stream.read(buffer, read, expectedLength - read)
            if (n == -1) break
            read += n
        }
        // One more read past what was expected: a body larger than declared
        // (or than expected, with no Content-Length at all) is refused the
        // same as one that came up short, never truncated and accepted.
        if (read != expectedLength || stream.read() != -1) null else buffer
    }
}

/**
 * Reads at most [limit] bytes of [connection]'s body, or `null` if there is
 * more — the same refusal [readExactly] makes for chunks, for a body whose
 * exact length is not known in advance but whose honest size is small.
 */
internal fun readAtMost(
    connection: HttpURLConnection,
    limit: Int,
): ByteArray? {
    if (connection.contentLengthLong > limit) return null
    return connection.inputStream.use { stream ->
        val buffer = ByteArray(limit + 1)
        var read = 0
        while (read <= limit) {
            val n = stream.read(buffer, read, limit + 1 - read)
            if (n == -1) break
            read += n
        }
        if (read > limit) null else buffer.copyOf(read)
    }
}

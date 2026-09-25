package playback

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Runs a real local HTTP server rather than faking `java.net` — the wire
 * format (status codes, headers, the signature) is the thing under test,
 * and only a real request/response round trip proves it byte-for-byte.
 */
class LanChunkClientTest {
    private val server = MockWebServer()
    private val client = LanChunkClient(connectTimeoutMs = 200, readTimeoutMs = 200)

    private fun baseUrl(): String = server.url("/").toString().removeSuffix("/")

    @After
    fun shutdownTheServer() = server.shutdown()

    /**
     * `docs/running-the-player.md`'s worked example, also asserted in Rust
     * (`token_tests.rs`) — the one place the two implementations are
     * checked against each other rather than only against themselves.
     */
    @Test
    fun theSharedTestVectorSignsToThePublishedSignature() {
        val token = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"

        val signature = sign(token, "PUT", "/v1/sets/abc123/chunks/0", total = 5, body = "hello".toByteArray())

        assertEquals("c1ac37f41c76c7c8434460c94458f58a817bf0859f9be493209d23a8ab9a4d85", signature)
    }

    @Test
    fun aTwoHundredWithTheExpectedLengthServesTheBytes() =
        runTest {
            val body = "abcdefghij"
            server.enqueue(MockResponse().setResponseCode(200).setBody(body))
            server.start()

            val chunk = client.get(baseUrl(), "s1", index = 0, expectedLength = body.length)

            assertContentEquals(body.toByteArray(), chunk)
        }

    @Test
    fun aTwoHundredWithTheWrongLengthIsAMiss() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("short"))
            server.start()

            val chunk = client.get(baseUrl(), "s1", index = 0, expectedLength = 999)

            assertNull(chunk)
        }

    @Test
    fun aFourOhFourIsAMiss() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(404))
            server.start()

            val chunk = client.get(baseUrl(), "s1", index = 0, expectedLength = 10)

            assertNull(chunk)
        }

    @Test
    fun aConnectionThatNeverAnswersFailsAsIoException() =
        runTest {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            server.start()

            assertFailsWith<IOException> { client.get(baseUrl(), "s1", index = 0, expectedLength = 10) }
        }

    /**
     * `avahi-browse` and a server's own status line both print a bare
     * `host:port`, and a viewer can type exactly that into the manual
     * address field. `URL(...)` throws `MalformedURLException` — an
     * `IOException` — for it; unlike [get] this must never escape as a
     * crash, since [verify] runs inside [LanServerLocator]'s discovery
     * coroutine with nothing above it to catch a stray exception.
     */
    @Test
    fun verifyOnASchemeLessAddressIsFalseRatherThanThrowing() =
        runTest {
            assertEquals(false, client.verify("192.168.1.5:7788"))
        }

    @Test
    fun statusOnASchemeLessAddressIsNullRatherThanThrowing() =
        runTest {
            assertNull(client.status("192.168.1.5:7788"))
        }

    @Test
    fun aChunkedBodyLongerThanExpectedIsAMissNotOverread() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setChunkedBody("abcdefghij", 4))
            server.start()

            assertNull(client.get(baseUrl(), "s1", index = 0, expectedLength = 5))
        }

    @Test
    fun aChunkedBodyShorterThanExpectedIsAMiss() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setChunkedBody("abc", 8))
            server.start()

            assertNull(client.get(baseUrl(), "s1", index = 0, expectedLength = 10))
        }

    @Test
    fun aFiveHundredOnGetFailsAsIoExceptionRatherThanAMiss() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(500))
            server.start()

            assertFailsWith<IOException> { client.get(baseUrl(), "s1", index = 0, expectedLength = 10) }
        }

    /**
     * Stands in for tracking a moving median of read latency — see
     * [LanChunkClient]'s own doc. A server that never answers at all
     * ([SocketPolicy.NO_RESPONSE]) would otherwise only fail once
     * [readTimeoutMs] elapses; here that is deliberately set far past
     * [getDeadlineMs], so the deadline — not the socket's own read timeout
     * — is what this test proves actually cuts the wait short.
     */
    @Test
    fun aServerThatNeverAnswersFailsAtTheDeadlineNotAtTheLongerReadTimeout() =
        runTest {
            val slowClient = LanChunkClient(connectTimeoutMs = 200, readTimeoutMs = 3_000, getDeadlineMs = 150)
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            server.start()

            assertFailsWith<IOException> { slowClient.get(baseUrl(), "s1", index = 0, expectedLength = 5) }
        }

    @Test
    fun anOversizedStatusBodyIsRefusedRatherThanReadWhole() =
        runTest {
            val padding = " ".repeat(64 * 1024)
            server.enqueue(MockResponse().setResponseCode(200).setBody("{\"held_bytes\":1,\"budget_bytes\":2,\"chunks\":1$padding}"))
            server.start()

            assertNull(client.status(baseUrl()))
        }
}

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

    @Test
    fun aPutCarriesTheSetTotalAndTheSignedAuthorizationHeader() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(201))
            server.start()
            val body = "hello".toByteArray()

            val result = client.put(baseUrl(), TOKEN, "abc123", index = 0, total = 5, body = body)

            assertEquals(LanPutResult.Stored, result)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/v1/sets/abc123/chunks/0", request.path)
            assertEquals("5", request.getHeader("X-Set-Total"))
            assertEquals(
                "MGC1 c1ac37f41c76c7c8434460c94458f58a817bf0859f9be493209d23a8ab9a4d85",
                request.getHeader("Authorization"),
            )
        }

    @Test
    fun aTwoHundredOnPutMeansAlreadyHeld() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200))
            server.start()

            assertEquals(LanPutResult.Stored, client.put(baseUrl(), TOKEN, "s1", 0, 5, "hello".toByteArray()))
        }

    @Test
    fun aFourOhOneOnPutIsUnauthorized() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(401))
            server.start()

            assertEquals(LanPutResult.Unauthorized, client.put(baseUrl(), TOKEN, "s1", 0, 5, "hello".toByteArray()))
        }

    @Test
    fun aFourHundredOnPutIsRejected() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(400))
            server.start()

            assertEquals(LanPutResult.Rejected, client.put(baseUrl(), TOKEN, "s1", 0, 5, "hello".toByteArray()))
        }

    @Test
    fun statusParsesTheHeldBudgetAndChunkCounts() =
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"version":"0.1.0","held_bytes":1048576,"budget_bytes":10485760,"chunks":1}""",
                ),
            )
            server.start()

            val status = client.status(baseUrl())

            assertEquals(LanServerStatus(1_048_576, 10_485_760, 1), status)
        }

    @Test
    fun statusIsNullOnAnythingButATwoHundred() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(500))
            server.start()

            assertNull(client.status(baseUrl()))
        }

    @Test
    fun verifyIsTrueOnlyOnATwoHundred() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200))
            server.enqueue(MockResponse().setResponseCode(500))
            server.start()

            assertEquals(true, client.verify(baseUrl()))
            assertEquals(false, client.verify(baseUrl()))
        }

    private companion object {
        const val TOKEN = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"
    }
}

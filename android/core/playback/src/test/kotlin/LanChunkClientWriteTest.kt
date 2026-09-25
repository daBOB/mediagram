package playback

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** PUT, `/v1/status`, and `verify` — split from [LanChunkClientTest] to keep each file under the line limit. */
class LanChunkClientWriteTest {
    private val server = MockWebServer()
    private val client = LanChunkClient(connectTimeoutMs = 200, readTimeoutMs = 200)

    private fun baseUrl(): String = server.url("/").toString().removeSuffix("/")

    @After
    fun shutdownTheServer() = server.shutdown()

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
    fun aPutStreamsWithAFixedContentLengthRatherThanChunking() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(201))
            server.start()

            client.put(baseUrl(), TOKEN, "s1", 0, 5, "hello".toByteArray())

            val request = server.takeRequest()
            assertEquals("5", request.getHeader("Content-Length"))
            assertNull(request.getHeader("Transfer-Encoding"))
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

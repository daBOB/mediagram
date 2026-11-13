package com.mediagram.spieltisch

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The game page stays on the box's own host. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GamePageClientTest {
    private val context = RuntimeEnvironment.getApplication()
    private val client = GamePageClient(context.assets)

    private fun leaves(url: String): Boolean = client.shouldOverrideUrlLoading(WebView(context), request(url))

    @Test
    fun theHostsOwnPagesOpen() {
        assertFalse(leaves("http://127.0.0.1:8080/tv?game=quiz"))
        assertFalse(leaves("http://127.0.0.1:8081/tv"))
    }

    @Test
    fun anywhereElseIsRefused() {
        assertTrue(leaves("https://example.com/"))
        assertTrue(leaves("http://192.168.0.2:8080/"))
        assertTrue(leaves("http://localhost:8080/tv"))
    }

    @Test
    fun theShelfArtComesFromTheApk() {
        val poster = assertNotNull(client.shouldInterceptRequest(WebView(context), request("http://127.0.0.1:8080/game-art/poster-quiz.webp")))
        assertEquals("image/webp", poster.mimeType)
        assertTrue(poster.data.readBytes().isNotEmpty())
    }

    @Test
    fun artThatIsNotThereIsAPlain404() {
        val none = assertNotNull(client.shouldInterceptRequest(WebView(context), request("http://127.0.0.1:8080/game-art/poster-no-such-game.webp")))
        assertEquals(404, none.statusCode)
        val sideways = assertNotNull(client.shouldInterceptRequest(WebView(context), request("http://127.0.0.1:8080/game-art/../secret.webp")))
        assertEquals(404, sideways.statusCode)
    }

    @Test
    fun everythingElseGoesToTheHost() {
        assertNull(client.shouldInterceptRequest(WebView(context), request("http://127.0.0.1:8080/qr.svg")))
        assertNull(client.shouldInterceptRequest(WebView(context), request("http://127.0.0.1:8080/game-art/poster-quiz.png")))
    }

    private fun request(url: String): WebResourceRequest =
        object : WebResourceRequest {
            override fun getUrl(): Uri = Uri.parse(url)

            override fun isForMainFrame() = true

            override fun isRedirect() = false

            override fun hasGesture() = false

            override fun getMethod() = "GET"

            override fun getRequestHeaders(): Map<String, String> = emptyMap()
        }
}

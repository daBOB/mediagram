package com.mediagram.spieltisch

import android.content.res.AssetManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.io.IOException

private const val AVATAR_PREFIX = "/game-avatars/"

/** The host's address as the TV page reaches it. */
private const val HOST = "127.0.0.1"

/**
 * The game page's client. It keeps the page on the box's own host: a link or redirect anywhere else
 * is refused, so the TV's full-screen view never shows another site.
 *
 * It answers the ceremony's avatar models from the APK's assets, so the 22 files exist once in the
 * APK instead of once per ABI inside the native core with the web client. The folder is generated and
 * gitignored (`bun crates/mediagram-game/tools/make-ceremony-avatars.mjs --out=android/spieltisch/src/main/assets/game-avatars`);
 * without it every request is a plain 404. Everything else goes to the game host.
 */
internal class GamePageClient(
    private val assets: AssetManager,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean = request.url.host != HOST

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val path = request.url.path ?: return null
        if (request.method != "GET" || !path.startsWith(AVATAR_PREFIX) || !path.endsWith(".glb")) return null
        val file = path.removePrefix(AVATAR_PREFIX)
        if (file.contains("/") || file.contains("..")) return notFound()
        return try {
            WebResourceResponse("model/gltf-binary", null, assets.open("game-avatars/$file"))
        } catch (_: IOException) {
            notFound()
        }
    }

    private fun notFound() = WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
}

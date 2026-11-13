package com.mediagram.spieltisch

import android.content.res.AssetManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.io.IOException

/** A folder of the APK's assets the page reads by URL: [prefix] + file name, of one [ending] and [type]. */
private class AssetFolder(
    val prefix: String,
    val folder: String,
    val ending: String,
    val type: String,
)

/**
 * The ceremony's avatar models (22 files, once in the APK instead of once per ABI inside the native
 * core with the web client; generated and gitignored, `bun crates/mediagram-game/tools/make-ceremony-avatars.mjs
 * --out=android/spieltisch/src/main/assets/game-avatars`) and the shelf's posters and backdrops. A file
 * that isn't there is a plain 404: the page draws a game without art on the felt.
 */
private val FOLDERS =
    listOf(
        AssetFolder("/game-avatars/", "game-avatars", ".glb", "model/gltf-binary"),
        AssetFolder("/game-art/", "game-art", ".webp", "image/webp"),
    )

/** The host's address as the TV page reaches it. */
private const val HOST = "127.0.0.1"

/**
 * The game page's client. It keeps the page on the box's own host: a link or redirect anywhere else
 * is refused, so the TV's full-screen view never shows another site.
 *
 * It answers the files in [FOLDERS] from the APK's assets; everything else goes to the game host.
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
        if (request.method != "GET") return null
        val from = FOLDERS.firstOrNull { path.startsWith(it.prefix) && path.endsWith(it.ending) } ?: return null
        val file = path.removePrefix(from.prefix)
        if (file.contains("/") || file.contains("..")) return notFound()
        return try {
            WebResourceResponse(from.type, null, assets.open("${from.folder}/$file"))
        } catch (_: IOException) {
            notFound()
        }
    }

    private fun notFound() = WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
}

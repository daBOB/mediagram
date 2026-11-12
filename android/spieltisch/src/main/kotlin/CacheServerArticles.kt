package com.mediagram.spieltisch

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/**
 * Preisfrage's articles on the home cache server the media app named ([server], `""` for none): the
 * list as a night starts, and each photo the first time the host asks for it. Reading needs no
 * token, so this app holds none. Whatever fails means no list or no photo, never no night.
 */
class CacheServerArticles(
    server: String,
    /** For connecting and again for reading. */
    private val timeoutMs: Int = 2_500,
) {
    private val base = server.trimEnd('/')

    /** The list's JSON, `""` when there is no server, no list, or no answer in time. */
    suspend fun list(): String {
        if (base.isEmpty()) return ""
        return withContext(Dispatchers.IO) {
            get("articles.json")?.decodeToString().orEmpty().also { Log.i(TAG, "article list from $base: ${it.length} chars") }
        }
    }

    /** A copy of the article's photo in [dir], fetched once; `null` when there is none. Blocks: the core calls it off its threads. */
    fun photo(
        asin: String,
        dir: File,
    ): String? {
        if (base.isEmpty() || !ASIN.matches(asin)) return null
        val copy = File(dir, "photo-$asin.jpg")
        if (copy.isFile) return copy.path
        val bytes = get("photos-$asin.jpg") ?: return null
        return try {
            dir.mkdirs()
            val part = File.createTempFile(copy.name, ".part", dir)
            try {
                part.writeBytes(bytes)
                // Moved into place whole, so a request meanwhile never reads half a file.
                copy.path.takeIf { part.renameTo(copy) }
            } finally {
                part.delete()
            }
        } catch (e: IOException) {
            Log.i(TAG, "photo $asin could not be kept: $e")
            null
        }
    }

    private fun get(name: String): ByteArray? {
        val connection = URI("$base/v1/game/preisfrage/$name").toURL().openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            if (connection.responseCode == HttpURLConnection.HTTP_OK) connection.inputStream.use { it.readBytes() } else null
        } catch (e: IOException) {
            Log.i(TAG, "$name from $base: $e")
            null
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TAG = "CacheServerArticles"

        /** A product's ASIN; anything else never reaches the server or names a file. */
        val ASIN = Regex("B[0-9A-Z]{9}")
    }
}

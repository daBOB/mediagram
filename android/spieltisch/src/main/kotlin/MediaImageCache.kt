package com.mediagram.spieltisch

import android.content.ContentResolver
import android.os.ParcelFileDescriptor
import android.util.Log
import uniffi.spieltisch_core.MediaImageKind
import uniffi.spieltisch_core.MediaImages
import java.io.File
import java.security.MessageDigest

/**
 * Copies a title's image out of the media app into [dir], once, and answers the copy's path for the
 * host to serve. The host only asks for tonight's titles, and the media app hands out only those.
 * Called by the core off its async threads, so blocking on the provider here is fine.
 */
class MediaImageCache(
    private val resolver: ContentResolver,
    private val dir: File,
) : MediaImages {
    @Suppress("TooGenericExceptionCaught") // Whatever fails, there is no image, and the host serves none.
    override fun fetch(
        key: String,
        kind: MediaImageKind,
    ): String? {
        // No extension: the host reads the type from the bytes. Hashed, so no key can name a path or run past a file name's length.
        val copy = File(dir, "${kind.name.lowercase()}-${sha256(key)}")
        if (copy.isFile) return copy.path
        val began = System.nanoTime()
        val ms = { (System.nanoTime() - began) / 1_000_000 }
        return try {
            dir.mkdirs()
            val part = File.createTempFile(copy.name, ".part", dir)
            try {
                // The descriptor stands alone once opened, so the client is not held for the copy.
                val file = resolver.withMediaApp { it.openFile(MediaApp.image(key, kind), "r") } ?: return null
                ParcelFileDescriptor.AutoCloseInputStream(file).use { from -> part.outputStream().use { from.copyTo(it) } }
                // Moved into place whole, so a request meanwhile never reads half a file.
                if (part.renameTo(copy)) copy.path.also { Log.i(TAG, "copied $kind for $key from the media app in ${ms()} ms") } else null
            } finally {
                part.delete()
            }
        } catch (e: Exception) {
            Log.i(TAG, "no $kind for $key from the media app after ${ms()} ms: $e")
            null
        }
    }

    private fun sha256(key: String) = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val TAG = "MediaImageCache"
    }
}

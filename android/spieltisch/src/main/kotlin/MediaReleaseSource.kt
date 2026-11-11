package com.mediagram.spieltisch

import android.content.ContentResolver
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import update.Lookup
import update.Release
import update.ReleaseSource
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Spieltisch's own releases, which only the media app can read from the channel. The row says which
 * release is newest; its APK is copied here and checked against that row before it counts, whatever
 * the core already checked on the other side.
 */
class MediaReleaseSource(
    private val resolver: ContentResolver,
    // The media app asks Telegram for the row; a stuck question must not hold every later check.
    private val limit: Duration = 60.seconds,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ReleaseSource {
    private class Row(
        val versionName: String,
        val versionCode: Long,
        val bytes: Long,
        val sha256: String,
    )

    override suspend fun latest(): Lookup {
        val began = System.nanoTime()
        return lookup().also { Log.i(TAG, "release from the media app in ${(System.nanoTime() - began) / 1_000_000} ms: ${describe(it)}") }
    }

    private fun describe(lookup: Lookup): String =
        when (lookup) {
            is Lookup.Unavailable -> "unavailable"
            is Lookup.Found -> lookup.release?.versionName ?: "none pinned"
        }

    private suspend fun lookup(): Lookup =
        try {
            withTimeoutOrNull(limit) { blocking { read(it) } } ?: Lookup.Unavailable(NOT_ANSWERED)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            // No media app, a refusal, or a row that is not a release: the same answer, and the next foreground asks again.
            Log.w(TAG, "the media app's release could not be read", e)
            Lookup.Unavailable(NOT_ANSWERED)
        }

    private fun read(signal: CancellationSignal): Lookup {
        val row =
            resolver.withMediaApp { client ->
                // No cursor is a lookup that failed over there; an empty one is nothing pinned.
                val cursor = client.query(MediaApp.release, null, null, null, null, signal) ?: return Lookup.Unavailable(NOT_ANSWERED)
                cursor.use {
                    if (!it.moveToFirst()) return Lookup.Found(null)
                    Row(
                        versionName = it.getString(it.getColumnIndexOrThrow(MediaApp.VERSION_NAME)),
                        versionCode = it.getLong(it.getColumnIndexOrThrow(MediaApp.VERSION_CODE)),
                        bytes = it.getLong(it.getColumnIndexOrThrow(MediaApp.BYTES)),
                        sha256 = it.getString(it.getColumnIndexOrThrow(MediaApp.SHA256)),
                    )
                }
            } ?: return Lookup.Unavailable(NOT_ANSWERED)
        if (row.versionName.isEmpty() || row.versionCode <= 0 || row.bytes <= 0 || !SHA256.matches(row.sha256)) {
            throw IOException("the media app's release row is not a release")
        }
        return Lookup.Found(Release(row.versionName, row.versionCode) { apk -> blocking { copy(row, apk, it) } })
    }

    /** The media app's download is awaited inside this call, so the file is whole or this throws. A copy that fails the row never reaches [apk]. */
    private fun copy(
        row: Row,
        apk: File,
        signal: CancellationSignal,
    ) {
        // Its own name, so a copy left running by a cancelled attempt cannot be written into by the next.
        val part = File.createTempFile("release", ".part", apk.parentFile)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            val source = resolver.withMediaApp { it.openFile(MediaApp.releaseApk, "r", signal) } ?: throw IOException(NOT_ANSWERED)
            ParcelFileDescriptor.AutoCloseInputStream(source).use { input ->
                part.outputStream().use { out ->
                    val buffer = ByteArray(COPY_BUFFER)
                    while (true) {
                        signal.throwIfCanceled()
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        size += n
                    }
                }
            }
            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            if (size != row.bytes || !sha256.equals(row.sha256, ignoreCase = true)) {
                throw IOException("the download is not the release the media app announced")
            }
            if (!part.renameTo(apk)) throw IOException("the download could not be put in place")
        } finally {
            part.delete()
        }
    }

    /**
     * A binder call cannot be interrupted, so it runs on its own and the caller waits for it
     * cancellably; giving up cancels [CancellationSignal], which reaches the media app's provider.
     */
    private suspend fun <T> blocking(call: (CancellationSignal) -> T): T {
        val signal = CancellationSignal()
        val pending = CoroutineScope(io).async { call(signal) }
        try {
            return pending.await()
        } catch (e: CancellationException) {
            signal.cancel()
            throw e
        }
    }

    private companion object {
        const val TAG = "MediaReleaseSource"
        const val NOT_ANSWERED = "the media app did not answer"
        const val COPY_BUFFER = 64 * 1024
        val SHA256 = Regex("[0-9a-fA-F]{64}")
    }
}

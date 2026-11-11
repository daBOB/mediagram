package com.mediagram.spieltisch

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import uniffi.spieltisch_core.MediaImageKind
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The media app's answer for a night: its profile's kids lock, and its titles as JSON in `GameTitle`'s shape. */
data class MediaNight(
    val kidsLocked: Boolean,
    val titles: String,
    /** False when the media app gave no answer, so the shelf can say why there are no films. */
    val answered: Boolean = true,
) {
    companion object {
        /** No answer: locked, with no films. */
        val Unanswered = MediaNight(kidsLocked = true, titles = "[]", answered = false)
    }
}

/** Where a night's films and kids lock come from. */
fun interface MediaLibrary {
    suspend fun night(): MediaNight

    /** Asks without waiting for the answer, so the media app is up and its night read by the time a night starts. */
    fun warmUp() {}
}

/** The media app's provider, which both apps' manifests name. */
object MediaApp {
    /** The provider's authority, `GameNightProvider.AUTHORITY` on the media app's side. */
    const val AUTHORITY = "com.mediagram.android.games"

    /** The night row's columns, as the media app's provider names them. */
    const val KIDS_LOCKED = "kids_locked"
    const val TITLES = "titles"

    /** One row: [KIDS_LOCKED] and [TITLES]. */
    val night: Uri = "content://$AUTHORITY/night".toUri()

    /** The release row's columns: Spieltisch's newest release, or no row. */
    const val VERSION_NAME = "version_name"
    const val VERSION_CODE = "version_code"
    const val BYTES = "bytes"
    const val SHA256 = "sha256"

    /** The paths the release is asked for at. */
    const val RELEASE = "release"
    const val RELEASE_APK = "release.apk"

    val release: Uri = "content://$AUTHORITY/$RELEASE".toUri()

    /** That release's APK, once the media app has downloaded and verified it. */
    val releaseApk: Uri = "content://$AUTHORITY/$RELEASE_APK".toUri()

    /** `/image/{key}/{poster|still}`, or `/textless/{key}`. */
    fun image(
        key: String,
        kind: MediaImageKind,
    ): Uri {
        val uri = Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(AUTHORITY)
        return when (kind) {
            MediaImageKind.TEXTLESS -> uri.appendPath("textless").appendPath(key)
            MediaImageKind.POSTER -> uri.appendPath("image").appendPath(key).appendPath("poster")
            MediaImageKind.STILL -> uri.appendPath("image").appendPath(key).appendPath("still")
        }.build()
    }
}

/**
 * Reads the night from the media app, off the main thread. No media app, a refusal, a row that is
 * not a night, or no answer within [limit] all mean [MediaNight.Unanswered]: never unlocked.
 */
class MediaLibraryBridge(
    private val resolver: ContentResolver,
    // The media app answers from its snapshot in about 1 s even cold; when it must build the night first, about 5 s on the emulator.
    private val limit: Duration = 8.seconds,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : MediaLibrary {
    override suspend fun night(): MediaNight {
        val began = System.nanoTime()
        val ms = { (System.nanoTime() - began) / 1_000_000 }
        // Not a child of the caller: a binder call can't be interrupted, so a late one is left to finish unheard.
        // Its own time is logged even then, since how long a cold media app takes is what the limit is set against.
        val answer = CoroutineScope(io).async { read().also { Log.i(TAG, "the media app's night read in ${ms()} ms") } }
        val night = withTimeoutOrNull(limit) { answer.await() } ?: MediaNight.Unanswered
        Log.i(TAG, "night from the media app in ${ms()} ms: answered=${night.answered} kids_locked=${night.kidsLocked} titles=${night.titles.length} chars")
        return night
    }

    /** One ask, its answer dropped: only starting the media app's process and its night is wanted. */
    override fun warmUp() {
        val began = System.nanoTime()
        CoroutineScope(io).launch {
            runCatching { resolver.withMediaApp { it.query(MediaApp.night, null, null, null, null)?.close() } }
            Log.i(TAG, "warmed the media app in ${(System.nanoTime() - began) / 1_000_000} ms")
        }
    }

    @Suppress("TooGenericExceptionCaught") // Whatever fails, the answer is the same: locked, no films.
    private fun read(): MediaNight? =
        try {
            resolver.withMediaApp { client ->
                client.query(MediaApp.night, null, null, null, null)?.use { row ->
                    if (!row.moveToFirst()) return null
                    val locked = row.getColumnIndexOrThrow(MediaApp.KIDS_LOCKED)
                    val titles = row.getString(row.getColumnIndexOrThrow(MediaApp.TITLES))
                    JSONArray(titles) // only a list of titles is an answer
                    // Unlocked only by an explicit 0.
                    MediaNight(kidsLocked = row.getType(locked) != Cursor.FIELD_TYPE_INTEGER || row.getInt(locked) != 0, titles = titles)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "the media app's night could not be read", e)
            null
        }

    private companion object {
        const val TAG = "MediaLibraryBridge"
    }
}

package playback

import android.util.Log
import data.CoreProvider
import kotlinx.coroutines.CancellationException

/**
 * A title's notes — its `summary` asset, as markdown — read from the index
 * the phone already holds, so opening them costs no round trip. The same
 * seam as [SubtitleTrackSource]: [DefaultSummarySource] in production, a
 * fake under test.
 */
interface SummarySource {
    /** The summary's text, or null when there is none (or the read failed). */
    suspend fun load(setId: String): String?

    companion object {
        /** Knows of no notes at all — what a test that never opens them gets by default. */
        val None: SummarySource = object : SummarySource {
            override suspend fun load(setId: String): String? = null
        }
    }
}

class DefaultSummarySource(private val coreProvider: CoreProvider) : SummarySource {
    /** A failed read is a line in logcat, never a reason to interrupt playback. */
    override suspend fun load(setId: String): String? = try {
        coreProvider.awaitCore().setText(setId, "summary", "")
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Log.w(TAG, "load($setId): ${e.message}")
        null
    }

    private companion object {
        const val TAG = "notes"
    }
}

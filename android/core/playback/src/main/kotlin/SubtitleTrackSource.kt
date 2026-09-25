package playback

import android.util.Log
import data.CoreProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A title's subtitle track, fetched and parsed. [DefaultSubtitleTrackSource]
 * is the production implementation, reading the index's own text through
 * [CoreProvider] the same way [data.PlayerPreferences] does; a fake stands
 * in for it under test.
 */
interface SubtitleTrackSource {
    /** [lang]'s cues for [setId], or empty when this file carries none (or the read/parse failed). */
    suspend fun load(setId: String, lang: String): List<TimedCue>
}

class DefaultSubtitleTrackSource(
    private val coreProvider: CoreProvider,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SubtitleTrackSource {
    /**
     * A missing track is ordinary (a film with no VTT for this language) and
     * says nothing. A round trip or a parse that actually failed is worth a
     * line in logcat — but never a reason to interrupt playback, which has
     * nothing to do with subtitles at all.
     */
    override suspend fun load(setId: String, lang: String): List<TimedCue> = try {
        val core = coreProvider.awaitCore()
        val vtt = core.setText(setId, "subtitle", lang) ?: return emptyList()
        // Parsing a feature film's transcript is real CPU work (a couple of
        // hundred KB is routine), and this is called from the same scope
        // that also drives playback — never on the caller's own dispatcher.
        withContext(dispatcher) { parseWebVttCues(vtt) }
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Log.w(TAG, "load($setId, $lang): ${e.message}")
        emptyList()
    }

    private companion object {
        const val TAG = "subtitles"
    }
}

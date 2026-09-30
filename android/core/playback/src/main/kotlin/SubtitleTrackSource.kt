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
    /** [track]'s cues for [setId] — its position among the set's own subtitle tracks — or empty when the read/parse failed. */
    suspend fun load(setId: String, track: Int): List<TimedCue>

    /**
     * Unused by any production caller — [load] is keyed by track position
     * now, not a language. Kept only so `ui-tv`'s own in-flight branch,
     * which still mocks the old by-language shape, keeps compiling; drop
     * once that branch has moved onto [load]'s own overload.
     */
    @Deprecated("kept for ui-tv's in-flight branch; use load(setId, track: Int)")
    suspend fun load(setId: String, lang: String): List<TimedCue> = emptyList()
}

class DefaultSubtitleTrackSource(
    private val coreProvider: CoreProvider,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SubtitleTrackSource {
    /**
     * A missing track is ordinary (a bundle not yet cached, say) and says
     * nothing. A round trip or a parse that actually failed is worth a line
     * in logcat — but never a reason to interrupt playback, which has
     * nothing to do with subtitles at all.
     */
    override suspend fun load(setId: String, track: Int): List<TimedCue> = try {
        val core = coreProvider.awaitCore()
        val vtt = core.subtitleText(setId, track.toUInt()) ?: return emptyList()
        // Parsing a feature film's transcript is real CPU work (a couple of
        // hundred KB is routine), and this is called from the same scope
        // that also drives playback — never on the caller's own dispatcher.
        withContext(dispatcher) { parseWebVttCues(vtt) }
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Log.w(TAG, "load($setId, $track): ${e.message}")
        emptyList()
    }

    private companion object {
        const val TAG = "subtitles"
    }
}

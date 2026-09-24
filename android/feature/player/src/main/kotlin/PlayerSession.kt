package player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The ten-second save ticker and the bookkeeping of which set is open —
 * split out of [PlayerViewModel] to keep that file under the project's
 * line guideline. Knows nothing about audio, subtitles or speed; only
 * which set is open and when to save where playback is.
 */
class PlayerSession(
    private val scope: CoroutineScope,
    private val handle: PlayerHandle,
    private val recorder: ProgressRecorder,
) {
    /** Which set [save] writes against; `null` between titles. */
    var openSetId: String? = null
        private set

    private var tickerJob: Job? = null

    /** A new title has opened; [save] and the ticker now write against it. */
    fun open(setId: String) {
        openSetId = setId
    }

    /** Clears the open set and stops the ticker, returning whichever set was open. */
    fun clear(): String? {
        val setId = openSetId
        stopTicking()
        openSetId = null
        return setId
    }

    /** Runs the ticker while playing; saves once and stops it otherwise. */
    fun onPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) startTicking() else { stopTicking(); save() }
    }

    fun stopTicking() {
        tickerJob?.cancel()
        tickerJob = null
    }

    /**
     * Writes wherever the player currently is, against whichever set is
     * open. A no-op with nothing open, nothing trustworthy yet
     * ([PlayerHandle.positionMs] is `null` before the player is ready or
     * after an error), or a position under [MIN_SAVE_MS]: an up-next switch
     * opens the next title before the old one's own synchronous "stopped
     * playing" event has finished landing, and that event still names the
     * set this now points to — without the floor, its otherwise-ordinary
     * pause-save would write the next episode onto Continue at 0s, before
     * a frame of it has actually played.
     */
    fun save() {
        val setId = openSetId ?: return
        val atMs = handle.positionMs() ?: return
        if (atMs < MIN_SAVE_MS) return
        val durationMs = handle.durationMs()
        scope.launch { recorder.save(setId, atMs / 1000.0, durationMs?.let { it / 1000.0 }) }
    }

    private fun startTicking() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (true) {
                delay(TICK_MS)
                save()
            }
        }
    }

    private companion object {
        const val TICK_MS = 10_000L
        const val MIN_SAVE_MS = 1_000L
    }
}

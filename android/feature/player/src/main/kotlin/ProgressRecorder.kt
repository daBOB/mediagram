package player

import android.util.Log
import data.ResumePoint
import data.WatchStateRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * Applies the web's save rule (`resume-point.js`, ported as [ResumePoint])
 * to one snapshot of where playback is: a position mid-title is kept, one
 * in the credits is dropped and the title stamped watched instead.
 *
 * Pure aside from the repository call it makes — no clock, no ExoPlayer —
 * so [PlayerViewModelTest] exercises this against a fake repository rather
 * than a real player. [WatchStateRepository] already writes off the main
 * thread and does nothing with no profile chosen, so this has neither
 * concern of its own; what it adds is the judgement of which write to make.
 */
class ProgressRecorder @Inject constructor(private val repository: WatchStateRepository) {

    /**
     * @param atSeconds where playback is now
     * @param observedDurationSeconds ExoPlayer's own reading of the set's
     *   length, trusted because Android always plays the original file
     *   directly — never a transcode whose reported length is still
     *   growing, the case [ResumePoint.trustedRuntime]'s `direct` flag
     *   guards against on the web.
     */
    suspend fun save(setId: String, atSeconds: Double, observedDurationSeconds: Double?) {
        runCatching {
            val runtime = ResumePoint.trustedRuntime(catalogued = null, observed = observedDurationSeconds, direct = true)
            if (ResumePoint.isFinished(atSeconds, runtime)) {
                repository.markFinished(setId)
            } else {
                repository.setProgress(setId, atSeconds, runtime.takeIf { it > 0 })
            }
        }.onFailure { error ->
            if (error is CancellationException) throw error
            // A save is a courtesy to the next time this title is opened,
            // never a reason to interrupt the one playing now.
            Log.w(TAG, "save: ${error.message}")
        }
    }

    private companion object {
        const val TAG = "progress"
    }
}

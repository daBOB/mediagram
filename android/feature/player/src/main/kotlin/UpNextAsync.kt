package player

import android.util.Log
import data.CatalogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import model.MediaSet

/**
 * The loops [UpNextController] runs — its phase ticker and countdown, and
 * the async work behind them — split out to keep that file under the
 * project's line guideline. None reads or writes its state directly; each
 * reports back through a callback instead.
 */

/** Runs [onTick] every [TICK_MS] until cancelled — the phase ticker, gated on play state by [UpNextController.onPlayingChanged]. */
internal fun CoroutineScope.runTicker(onTick: () -> Unit): Job = launch {
    while (true) {
        delay(TICK_MS)
        onTick()
    }
}

/** Runs the ten-second countdown, reporting each second left through [onTick], then [onFinished] once it reaches zero. */
internal fun CoroutineScope.runCountdown(onTick: (secondsLeft: Int) -> Unit, onFinished: () -> Unit): Job = launch {
    var left = COUNTDOWN_SECONDS
    while (left > 0) {
        delay(1_000)
        left -= 1
        onTick(left)
    }
    onFinished()
}

/**
 * Resolves [id]'s [MediaSet] and hands it to [onResolved] once it lands —
 * cancellation aside, a lookup failing here is not a reason to break the
 * player, so anything else is reported as `null`.
 */
internal fun CoroutineScope.resolveUpNextTitle(
    catalogRepository: CatalogRepository,
    id: String,
    onResolved: (MediaSet?) -> Unit,
): Job = launch {
    val set = try {
        catalogRepository.mediaSet(id)
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        null
    }
    onResolved(set)
}

/**
 * Polls the autoplay gate every [GATE_POLL_MS] until [autoplayReady] says an
 * unattended start may begin, or the loader itself has stopped with
 * anything at all held ([loaderStopped]), then calls [onReady] and plays.
 * Polled rather than driven by events, the same as the web: the condition
 * is a buffer length, which no single event announces.
 */
internal fun CoroutineScope.pollAutoplayGate(handle: PlayerHandle, runtimeSecs: Double?, onReady: () -> Unit): Job = launch {
    var waitedMs = 0L
    while (true) {
        val posMs = handle.positionMs() ?: 0L
        val bufferedMs = handle.bufferedPositionMs() ?: posMs
        val at = AutoplayAt((bufferedMs - posMs) / 1_000.0, runtimeSecs?.let { it - posMs / 1_000.0 }, waitedMs)
        if (autoplayReady(at) || loaderStopped(handle, at)) {
            Log.d(TAG, "autoplay gate satisfied: ahead=${at.aheadSeconds}s remaining=${at.remainingSeconds}s waited=${at.waitedMs}ms loading=${handle.isLoading()}")
            onReady()
            handle.play()
            return@launch
        }
        Log.d(TAG, "autoplay gate waiting: ahead=${at.aheadSeconds}s remaining=${at.remainingSeconds}s waited=${at.waitedMs}ms")
        delay(GATE_POLL_MS)
        waitedMs += GATE_POLL_MS
    }
}

/**
 * media3's default load control caps how far it will ever buffer ahead —
 * well under the web's own 60s `READY_SECONDS` — so a loader that has
 * stopped with anything at all held is as ready as it is ever going to get,
 * whatever the ported threshold says. A phone-only addition beside the
 * ported constant, not a change to it.
 */
private fun loaderStopped(handle: PlayerHandle, at: AutoplayAt) = !handle.isLoading() && at.aheadSeconds > 0

private const val GATE_POLL_MS = 500L
private const val TICK_MS = 1_000L
private const val TAG = "UpNext"

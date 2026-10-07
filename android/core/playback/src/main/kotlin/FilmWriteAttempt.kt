package playback

import data.orDefault
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * One write attempt for one film — split out of [FilmPreloader] since it
 * touches only [lane], [writer], [openTitleSource] and [network], never
 * the queue or the per-film state map. [FilmPreloader] paces [onProgress]
 * into throttled state; this class only decides when the write itself
 * must stop.
 *
 * Two watchdogs race the write: an open title reacts the instant one
 * opens, a metered network is only ever known by asking, so it polls
 * every [METERED_RECHECK_MS] — the same interval [FilmPreloader]'s own
 * top-of-loop gate uses between attempts. Both are cancelled the moment
 * the write itself settles; `supervisorScope` would otherwise not return
 * until every child does, watchdogs included.
 */
internal class FilmWriteAttempt(
    private val lane: DownloadLane,
    private val writer: FilmPreloadWriter,
    private val openTitleSource: OpenTitleSource,
    private val network: UnmeteredNetworkCheck,
) {
    /**
     * [heldAtStart] is reported through [onProgress] the moment [lane] is
     * actually acquired — never before, so a caller showing `Running` from
     * that same callback never shows it while merely waiting for the lane.
     */
    suspend fun run(item: PreloadItem, heldAtStart: Long, onProgress: (bytesCached: Long) -> Unit): WriteOutcome =
        lane.withLane {
            // supervisorScope, not coroutineScope: a plain scope treats the
            // write async failing as a scope-wide failure and cancels the
            // watchdogs and rethrows before runCatchingWrite's own catch
            // ever gets a say — one write failure would then look, from
            // the caller's point of view, exactly like an unexpected
            // crash, skipping the whole retry/backoff path for a
            // completely ordinary transient error. A supervisor isolates
            // the write's own failure so this scope, and its watchdogs,
            // stay in charge.
            supervisorScope {
                onProgress(heldAtStart)
                val throttle = ProgressThrottle()
                // Set by either watchdog before it cancels the write, so
                // runCatchingWrite can tell "we did this on purpose" from
                // a genuine failure — CacheWriter.cancel() makes
                // CacheWriter.cache() throw InterruptedIOException, an
                // ordinary exception, not a CancellationException, so the
                // exception type alone cannot make that distinction.
                var interrupted = false
                val write =
                    async {
                        writer.write(item) { bytesCached ->
                            if (throttle.shouldEmit()) onProgress(bytesCached)
                        }
                    }
                val watchOpen =
                    launch {
                        openTitleSource.openTitle.filter { it != null }.first()
                        interrupted = true
                        write.cancel()
                    }
                val watchMetered =
                    launch {
                        orDefault(Unit) {
                            while (isActive) {
                                delay(METERED_RECHECK_MS)
                                if (!network.isUnmetered()) {
                                    interrupted = true
                                    write.cancel()
                                    break
                                }
                            }
                        }
                    }
                val outcome = runCatchingWrite(write) { interrupted }
                watchOpen.cancel()
                watchMetered.cancel()
                outcome
            }
        }

    @Suppress("TooGenericExceptionCaught") // a preload that fails costs nothing but the wait it was meant to save — the caller's own retry loop decides what happens next
    private suspend fun runCatchingWrite(write: Deferred<Unit>, wasInterrupted: () -> Boolean): WriteOutcome =
        try {
            write.await()
            WriteOutcome.Completed
        } catch (e: CancellationException) {
            if (wasInterrupted()) WriteOutcome.Interrupted else throw e
        } catch (e: Exception) {
            if (wasInterrupted()) WriteOutcome.Interrupted else WriteOutcome.Failed(e)
        }
}

private const val METERED_RECHECK_MS = 5_000L

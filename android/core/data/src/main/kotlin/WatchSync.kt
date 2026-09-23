package data

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import settings.LibrarySettings
import uniffi.mediagram_core.LibraryEvent
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The watch-state sync cadence: a round on app start, every five minutes
 * while foreground, on leaving the player (phase 05 calls [soon]), and one
 * last try on [onBackground]. Mirrors the web's own cadence — awaited at
 * start, a timer, one last round on `SIGTERM` — for a device that is killed
 * rather than closed, where `onStop` is the closest thing to a signal.
 */
interface WatchSync {
    /**
     * One round now, then every five minutes until [onBackground]. Safe to
     * call again while already foreground — a second activity instance
     * starting does not stack a second timer.
     */
    fun onForeground()

    /** Cancels the timer, then starts one last round in this scope's own lifetime, so it can finish after the caller is gone. */
    fun onBackground()

    /** One round outside the timer's own cadence. Queues behind whatever round is already running rather than running beside it. */
    fun soon()

    /**
     * Suspends until the round [onForeground] started has settled, so a
     * caller that needs this device's viewers complete — the picker — is
     * never shown a list still missing what another device just wrote.
     * Returns at once if a round has already settled, or if a library has
     * never been chosen.
     */
    suspend fun awaitFirstRound()
}

/**
 * Runs only when a library is chosen and the core is authorised: [round]
 * quietly does nothing otherwise, which covers a device still mid setup —
 * [android.app.Activity.onStart] calls [onForeground] unconditionally, long
 * before that is decided.
 *
 * Every round is wrapped so nothing it does can throw past this class: a
 * channel that cannot be reached is exactly the case this exists to survive,
 * not to crash over.
 */
class DefaultWatchSync(
    private val coreProvider: CoreProvider,
    private val settings: LibrarySettings,
    private val repository: WatchStateRepository,
    private val libraryEvents: LibraryEvents,
    private val scope: CoroutineScope,
    private val period: Duration = 5.minutes,
) : WatchSync {

    // Guards the round itself: a timer tick, a pushed STATE event and a
    // leave-the-player call can all land at once, and only one may touch
    // syncState at a time — the core would serialize them anyway, but a
    // second one queued here never even builds its own core call.
    private val roundLock = Mutex()

    private val firstRound = CompletableDeferred<Unit>()

    // The timer and the STATE-event listener, together, so one
    // onBackground() cancels both. Reassigned on every onForeground(), so a
    // second call replaces rather than stacks a job.
    private var foregroundJob: Job? = null

    override fun onForeground() {
        if (foregroundJob?.isActive == true) return
        foregroundJob = scope.launch {
            launch { libraryEvents.events().filter { it == LibraryEvent.STATE }.collect { soon() } }
            while (true) {
                round("timer")
                delay(period)
            }
        }
    }

    override fun onBackground() {
        foregroundJob?.cancel()
        foregroundJob = null
        scope.launch { round("background") }
    }

    override fun soon() {
        scope.launch { round("soon") }
    }

    override suspend fun awaitFirstRound() {
        firstRound.await()
    }

    private suspend fun round(why: String) {
        roundLock.withLock {
            val handle = settings.read()
            if (handle != null) runCatching { attempt(handle, why) }.onFailure {
                if (it is CancellationException) throw it
                Log.w(TAG, "$why: ${it.message}")
            }
        }
        firstRound.complete(Unit)
    }

    private suspend fun attempt(handle: String, why: String) {
        val core = coreProvider.awaitCore()
        if (!core.isAuthorized()) return
        val outcome = core.syncState(handle)
        outcome.failed?.let { Log.w(TAG, "$why: $it") }
        if (outcome.pulled > 0uL) repository.reload()
    }

    private companion object {
        const val TAG = "sync"
    }
}

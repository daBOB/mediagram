package data

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import settings.LibrarySettings
import uniffi.mediagram_core.LibraryEvent
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The watch-state sync cadence: a round on app start, every five minutes
 * while foreground, on leaving the player through [soon], and one
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

    /** One round outside the timer's cadence. News arriving during a round retains a follow-up read. */
    fun soon()

    /**
     * Requests or joins a round for the current core and library. A choice
     * made during setup cannot inherit readiness from an earlier identity.
     * Returns immediately with no library chosen; callers may bound their
     * wait without cancelling the shared round already doing useful work.
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
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultWatchSync(
    private val coreProvider: CoreProvider,
    private val settings: LibrarySettings,
    private val repository: WatchStateRepository,
    private val libraryEvents: LibraryEvents,
    private val scope: CoroutineScope,
    private val period: Duration = 5.minutes,
) : WatchSync {
    // Requests for the same identity join one app-scope round. A new identity
    // cancels and joins the old work before starting, so native calls never
    // overlap and a picker timeout does not cancel another caller's round.
    private val roundLock = Mutex()

    private data class Running(
        val core: CoreClient,
        val handle: String,
        val task: Deferred<Unit>,
        var again: Boolean = false,
    )

    private var running: Running? = null

    // The timer and the STATE-event listener, together, so one
    // onBackground() cancels both. Reassigned on every onForeground(), so a
    // second call replaces rather than stacks a job.
    private var foregroundJob: Job? = null

    override fun onForeground() {
        if (foregroundJob?.isActive == true) return
        foregroundJob =
            scope.launch {
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
        try {
            if (settings.read() == null) return
            coreProvider.coreOrNull()
            combine(coreProvider.core, settings.selections()) { core, handle -> core to handle }
                .mapLatest { (core, handle) ->
                    if (core != null && handle != null) round("picker", followUp = false)
                }.first()
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            Log.w(TAG, "picker: ${failure.message}")
        }
    }

    private suspend fun round(
        why: String,
        followUp: Boolean = true,
    ) {
        try {
            var queueFollowUp = followUp
            while (true) {
                val handle = settings.read() ?: return
                val core = coreProvider.coreOrNull() ?: return
                val task = startOrJoin(core, handle, why, queueFollowUp)
                try {
                    task.await()
                    return
                } catch (failure: CancellationException) {
                    // Replacing an identity cancels its task, not a still
                    // interested caller. Actual caller/app cancellation goes.
                    currentCoroutineContext().ensureActive()
                    scope.coroutineContext.ensureActive()
                    if (roundLock.withLock { running?.task === task }) throw failure
                    queueFollowUp = false
                }
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            Log.w(TAG, "$why: ${failure.message}")
        }
    }

    private suspend fun startOrJoin(
        core: CoreClient,
        handle: String,
        why: String,
        followUp: Boolean,
    ): Deferred<Unit> =
        roundLock.withLock {
            val previous = running
            if (previous?.core === core && previous.handle == handle && previous.task.isActive) {
                if (followUp) previous.again = true
                return@withLock previous.task
            }
            lateinit var current: Running
            val task =
                scope.async<Unit>(start = CoroutineStart.LAZY) {
                    previous?.task?.cancelAndJoin()
                    do {
                        try {
                            attempt(core, handle, why)
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (failure: Exception) {
                            Log.w(TAG, "$why: ${failure.message}")
                        }
                    } while (roundLock.withLock {
                            val again = running === current && current.again
                            current.again = false
                            if (!again && running === current) running = null
                            again
                        }
                    )
                }
            current = Running(core, handle, task)
            running = current
            task.start()
            task
        }

    private suspend fun attempt(
        core: CoreClient,
        handle: String,
        why: String,
    ) {
        // A queued request belongs to the identity that received it. A picker
        // may not be open to replace this task when selection changes.
        if (coreProvider.core.value !== core || settings.read() != handle) return
        if (!core.isAuthorized()) return
        val outcome = core.syncState(handle)
        outcome.failed?.let { Log.w(TAG, "$why: $it") }
        if (outcome.pulled > 0uL) repository.reload()
    }

    private companion object {
        const val TAG = "sync"
    }
}

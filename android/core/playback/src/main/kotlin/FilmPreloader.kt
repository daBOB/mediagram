package playback

import data.coreSentence
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Takes one film a viewer chose into the cache in full, through
 * [FilmWriteAttempt] and the same [DownloadLane] `SeriesPreloader` uses.
 *
 * Every film ever asked about keeps a small [FilmPreloadState] override
 * ([overrides]), read back through [stateOf]. `null` means "nothing to
 * report beyond what the cache already holds", which [stateOf] answers by
 * reading [heldSets] fresh — the same reasoning that makes a cancelled or
 * removed film settle back to its real percentage, and that keeps
 * re-checking a `Done` film against real held bytes rather than trusting
 * an override eviction could have since made false.
 *
 * Each running item gets its own [Job] ([itemJobs]), which [cancel],
 * [remove] and [pauseForTimeLimit] cancel directly — every wait inside
 * [runItem] (paused for an open title, a metered recheck, a retry
 * backoff) and the write itself are all ordinary suspend points, so
 * cancelling that one `Job` ends whichever of them is current for free.
 */
class FilmPreloader(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    writer: FilmPreloadWriter,
    lane: DownloadLane,
    private val heldSets: HeldSetsQuery,
    private val removeFromCache: suspend (setId: String) -> Unit,
    private val openTitleSource: OpenTitleSource,
    private val network: UnmeteredNetworkCheck,
    /** Whether a film this size can ever fit, reserving [reservedBytes] for whatever else must not be evicted — see `fitsFilmPreloadBudget`. */
    private val fits: suspend (totalBytes: Long, reservedBytes: Long) -> Boolean,
    private val log: (String) -> Unit = {},
) : FilmPreloading {
    private val writeAttempt = FilmWriteAttempt(lane, writer, openTitleSource, network)

    private val queue = FilmPreloadQueue()
    override val hasWork: StateFlow<Boolean> = queue.hasWork

    /** Something changed the queue — wakes the worker from an empty-queue wait. Conflated: only "there is work" matters, not how many times. */
    private val wake = Channel<Unit>(Channel.CONFLATED)

    private val overrides = ConcurrentHashMap<String, MutableStateFlow<FilmPreloadState?>>()

    /** The running `Job` for whichever item is active, keyed by set id — see the class doc. Only ever written from [dispatcher], so [cancel]/[remove]/[pauseForTimeLimit] read it from there too rather than racing `runWorker`'s own registration of a just-started item. */
    private val itemJobs = ConcurrentHashMap<String, Job>()

    /** Set ids whose final settling [remove] or [pauseForTimeLimit] is doing itself, after their own extra step (clearing the cache, reading a fresh held count) — [runItem]'s own `finally` must not race that with its own generic settling. */
    private val externallySettled = ConcurrentHashMap.newKeySet<String>()

    private val _heldEvents = MutableSharedFlow<String>(extraBufferCapacity = EVENTS_BUFFER)
    override val heldEvents: SharedFlow<String> = _heldEvents.asSharedFlow()

    private val _unheldEvents = MutableSharedFlow<String>(extraBufferCapacity = EVENTS_BUFFER)
    override val unheldEvents: SharedFlow<String> = _unheldEvents.asSharedFlow()

    private val _active = MutableStateFlow<ActivePreload?>(null)
    override val active: StateFlow<ActivePreload?> = _active

    init {
        scope.launch(dispatcher) { runWorker() }
    }

    override fun stateOf(setId: String, totalBytes: Long): Flow<FilmPreloadState> =
        override(setId).map { ov ->
            when {
                ov == null -> FilmPreloadState.Idle(heldSets.heldBytes(setId, totalBytes), totalBytes)
                // Re-verified on every observation, not trusted forever: a
                // lowered budget or a later eviction (there is no pinning)
                // must not leave "Preloaded ✓" showing for a film that no
                // longer is.
                ov is FilmPreloadState.Done -> {
                    val held = heldSets.heldBytes(setId, totalBytes)
                    if (held >= totalBytes) ov else FilmPreloadState.Idle(held, totalBytes)
                }
                else -> ov
            }
        }

    override fun enqueue(setId: String, title: String, totalBytes: Long) {
        // A set of unknown size is never held (HeldSetsQuery's own rule);
        // taking it anyway would light the catalogue's badge for nothing.
        if (totalBytes <= 0) return
        if (!queue.enqueue(PreloadItem(setId, title, totalBytes))) return
        override(setId).value = FilmPreloadState.Queued
        wake.trySend(Unit)
    }

    /**
     * Clears [setId] from [queue] synchronously, before asking anything to
     * cancel — a viewer tapping Preload again right after Cancel must find
     * the slot already free, not still claimed by a write that has only
     * just been asked to stop and has not unwound yet.
     */
    override fun cancel(setId: String) {
        val wasActive = queue.cancel(setId) != null
        if (!wasActive) override(setId).value = null
        // The job lookup happens on dispatcher, the same single thread
        // runWorker registers it from — reading itemJobs from the caller's
        // own thread could land in the gap between runWorker marking an
        // item active and it actually storing the Job, missing it entirely.
        scope.launch(dispatcher) { itemJobs[setId]?.cancel() }
    }

    override fun remove(setId: String) {
        externallySettled += setId
        queue.cancel(setId)
        scope.launch(dispatcher) {
            itemJobs[setId]?.let {
                it.cancel()
                it.join()
            }
            // The write, if any, has now genuinely stopped — safe to clear
            // the cache without racing a still-unwinding write for the
            // same key.
            safely("could not remove $setId from the cache") { removeFromCache(setId) }
            externallySettled -= setId
            // Cache cleared before this settles, not after: a live
            // collector's one emission on this transition must already
            // read zero, not whatever was held a moment before the clear.
            override(setId).value = null
            _unheldEvents.emit(setId)
        }
    }

    /**
     * Android's own ceiling for a continuously running `dataSync`
     * foreground service — not just the film actually writing: anything
     * still queued would otherwise run right on without one, which the
     * platform does not allow starting from the background. Every film
     * this touches is dropped from [queue] entirely, the same as
     * [cancel]/[remove] — resuming any of them, active or merely queued,
     * is a fresh [enqueue] once the viewer is back.
     */
    override fun pauseForTimeLimit() {
        scope.launch(dispatcher) {
            val activeItem = queue.activeItem
            val pendingItems = queue.drainPending()
            if (activeItem != null) {
                externallySettled += activeItem.setId
                queue.cancel(activeItem.setId)
                itemJobs[activeItem.setId]?.let {
                    it.cancel()
                    it.join()
                }
                externallySettled -= activeItem.setId
                pauseForTimeLimit(activeItem)
            }
            for (item in pendingItems) pauseForTimeLimit(item)
        }
    }

    private suspend fun pauseForTimeLimit(item: PreloadItem) {
        val held = heldSets.heldBytes(item.setId, item.totalBytes)
        override(item.setId).value = FilmPreloadState.Paused(held, item.totalBytes, PauseReason.TimeLimit)
    }

    private fun override(setId: String): MutableStateFlow<FilmPreloadState?> =
        overrides.getOrPut(setId) { MutableStateFlow(null) }

    private suspend fun runWorker() {
        while (true) {
            val item = queue.nextToRun()
            if (item == null) {
                wake.receive()
                continue
            }
            val job = scope.launch(dispatcher) { runItem(item) }
            itemJobs[item.setId] = job
            job.join()
            itemJobs.remove(item.setId, job)
        }
    }

    /**
     * One film, from wherever it last stopped to a final state — always
     * through [settle] in `finally`, which runs whether this returned on
     * its own, was cancelled by [cancel]/[remove]/[pauseForTimeLimit], or
     * a dependency ([heldSets], [fits]) threw something nobody expected.
     * That last case is caught here specifically so one film's broken
     * lookup fails only that film rather than killing [runWorker] for
     * every film after it.
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod") // one state machine reads better together than split across call sites that would each need the same item/held/queue context
    private suspend fun runItem(item: PreloadItem) {
        // Not in init: that runs the moment this class is constructed,
        // which — now that the catalogue holds a FilmPreloading too — is
        // as soon as it opens, not when a film is actually preloaded.
        // Suspends until the very first read below is trustworthy, not
        // just fired-and-forgotten (see OpenTitleSource.ensureListening).
        openTitleSource.ensureListening()
        var outcome: ItemOutcome = ItemOutcome.Interrupted
        try {
            var consecutiveFailures = 0
            var bytesAtLastFailure = -1L
            while (true) {
                val held = heldSets.heldBytes(item.setId, item.totalBytes)
                if (held >= item.totalBytes) {
                    outcome = ItemOutcome.Done
                    return
                }

                val open = openTitleSource.openTitle.value
                val reserved = if (open != null && open.setId != item.setId) open.totalBytes else 0L
                if (!fits(item.totalBytes, reserved) && (reserved <= 0L || !fits(item.totalBytes, 0L))) {
                    // Fails even with nothing reserved — no open title,
                    // now or later, could ever be the reason. A film that
                    // only fails because of the *current* reserve falls
                    // through to the open-title pause below instead, and
                    // is re-judged fresh once that title closes.
                    outcome = ItemOutcome.NeedsSpace(item.totalBytes)
                    return
                }

                if (open != null) {
                    override(item.setId).value = FilmPreloadState.Paused(held, item.totalBytes, PauseReason.Playing)
                    openTitleSource.openTitle.filter { it == null }.first()
                    continue
                }
                if (!network.isUnmetered()) {
                    override(item.setId).value = FilmPreloadState.Paused(held, item.totalBytes, PauseReason.Metered)
                    delay(METERED_RECHECK_MS)
                    continue
                }

                when (val result = writeAttempt.run(item, held) { bytes -> setRunning(item, bytes) }) {
                    WriteOutcome.Completed -> { outcome = ItemOutcome.Done; return }
                    // A retry, not a stop: the open-title/metered watchdog
                    // fired mid-write. The loop re-reads both gates fresh
                    // above and pauses correctly on its own.
                    WriteOutcome.Interrupted -> continue
                    is WriteOutcome.Failed -> {
                        val heldNow = heldSets.heldBytes(item.setId, item.totalBytes)
                        if (heldNow > bytesAtLastFailure) {
                            consecutiveFailures = 0
                            bytesAtLastFailure = heldNow
                        } else {
                            consecutiveFailures++
                        }
                        log("film preload: ${item.title} write failed: ${result.cause.message}")
                        if (consecutiveFailures > MAX_CONSECUTIVE_FAILURES) {
                            outcome = ItemOutcome.Failed(result.cause.coreSentence() ?: "Could not preload this film")
                            return
                        }
                        delay(backoffDelayMs(consecutiveFailures))
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") // one film's broken lookup (heldSets, fits) must not kill runWorker for every film after it
            e: Exception,
        ) {
            log("film preload: ${item.title} could not be checked: ${e.message}")
            outcome = ItemOutcome.Failed(e.coreSentence() ?: "Could not preload this film")
        } finally {
            if (item.setId !in externallySettled) settle(item, outcome)
            if (queue.activeId == item.setId) queue.clearActive()
            _active.value = null
        }
    }

    private suspend fun settle(item: PreloadItem, outcome: ItemOutcome) {
        when (outcome) {
            is ItemOutcome.Done -> {
                override(item.setId).value = FilmPreloadState.Done
                _heldEvents.emit(item.setId)
                log("film preload: ${item.title} held")
            }
            is ItemOutcome.NeedsSpace -> override(item.setId).value = FilmPreloadState.NeedsSpace(outcome.neededBytes)
            is ItemOutcome.Failed -> override(item.setId).value = FilmPreloadState.Failed(outcome.reason)
            // Cancelled, or removed mid-loop before externallySettled was
            // recorded — settle back to a fresh read, never a frozen number.
            ItemOutcome.Interrupted -> override(item.setId).value = null
        }
    }

    private fun setRunning(item: PreloadItem, heldBytes: Long) {
        override(item.setId).value = FilmPreloadState.Running(heldBytes, item.totalBytes)
        _active.value = ActivePreload(item.title, heldBytes, item.totalBytes)
    }

    @Suppress("TooGenericExceptionCaught") // logged and swallowed on purpose — see the call site
    private suspend fun safely(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("film preload: $what: ${e.message}")
        }
    }
}

private const val EVENTS_BUFFER = 8
private const val METERED_RECHECK_MS = 5_000L
private const val MAX_CONSECUTIVE_FAILURES = 8

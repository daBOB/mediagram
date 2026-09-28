package playback

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * One row of [FilmPreloader]'s own queue, ordered: the film actually
 * writing — or paused, waiting for its own reason to clear — first, if
 * there is one, then every film still waiting its turn in FIFO order.
 *
 * What a Preloads page's three sections and a queued film's own "ahead"
 * label both read, rather than either re-deriving the engine's own order
 * from [FilmPreloadQueue] directly.
 */
sealed interface FilmPreloadRow {
    val setId: String
    val title: String
    val totalBytes: Long

    /** The film at the front of the queue — actually writing, or paused with [pauseReason] naming why. */
    data class Running(
        override val setId: String,
        override val title: String,
        override val totalBytes: Long,
        val heldBytes: Long,
        /** `null` while the write itself is progressing; set while paused for that reason. */
        val pauseReason: PauseReason?,
    ) : FilmPreloadRow

    /** Waiting behind [Running] (or, for a moment, about to become it) — never started. */
    data class Waiting(
        override val setId: String,
        override val title: String,
        override val totalBytes: Long,
    ) : FilmPreloadRow

    /**
     * Paused by Android's own background-service time limit
     * ([PauseReason.TimeLimit]) — reported apart from [QueueSnapshot], not
     * derived from it, since [FilmPreloader.pauseForTimeLimit] empties the
     * real queue for every film it touches; only a fresh [FilmPreloader.enqueue]
     * (the Preloads page's own Resume) puts one back on it. [wasActive]
     * marks the one that was actually writing, so a Preloads page can keep
     * it under Preloading rather than Queued — the same place it held
     * before the pause.
     */
    data class TimeLimitPaused(
        override val setId: String,
        override val title: String,
        override val totalBytes: Long,
        val heldBytes: Long,
        val wasActive: Boolean,
    ) : FilmPreloadRow
}

/**
 * [snapshot]'s active film, if any, plus every one still pending, in
 * order. [activeState] is whatever [FilmPreloader.stateOf] reports for
 * that film right now — read fresh by the caller rather than kept here, so
 * a resumed write's held-byte figure never lags what the film page itself
 * shows.
 *
 * A film [FilmPreloadQueue] already marks active reads [Waiting] until
 * `FilmWriteAttempt` actually takes the shared lane — a series preload can
 * hold it for the whole of an episode's write, and `stateOf` still answers
 * [FilmPreloadState.Queued] for as long as that lasts, the same "never
 * shows Running while merely waiting for the lane" rule the writer itself
 * documents. It still leads every row from [snapshot.pending], since it is
 * still the next film to actually write.
 */
fun filmPreloadRows(snapshot: QueueSnapshot, activeState: FilmPreloadState?): List<FilmPreloadRow> {
    val rows = mutableListOf<FilmPreloadRow>()
    snapshot.active?.let { active ->
        rows +=
            when (activeState) {
                is FilmPreloadState.Running ->
                    FilmPreloadRow.Running(active.setId, active.title, active.totalBytes, activeState.heldBytes, pauseReason = null)
                is FilmPreloadState.Paused ->
                    FilmPreloadRow.Running(active.setId, active.title, active.totalBytes, activeState.heldBytes, activeState.reason)
                else ->
                    FilmPreloadRow.Waiting(active.setId, active.title, active.totalBytes)
            }
    }
    snapshot.pending.forEach { rows += FilmPreloadRow.Waiting(it.setId, it.title, it.totalBytes) }
    return rows
}

/**
 * [FilmPreloader.queueOverview] itself, built from [snapshot] and a way to
 * ask what the active film's own state is right now — kept apart from
 * [FilmPreloader] so that class does not grow for a page it does not
 * otherwise know about. Re-asks [stateOf] only when [snapshot] actually
 * changes (a new film became active, or the pending order moved), not on
 * every one of that film's own progress ticks — [stateOf] is already a
 * `Flow`, so this only has to switch to a new one when there is a new film
 * to watch.
 */
fun filmPreloadOverview(
    snapshot: Flow<QueueSnapshot>,
    stateOf: (setId: String, totalBytes: Long) -> Flow<FilmPreloadState>,
): Flow<List<FilmPreloadRow>> =
    snapshot.flatMapLatest { snap ->
        val active = snap.active
        if (active == null) flowOf(filmPreloadRows(snap, null)) else stateOf(active.setId, active.totalBytes).map { filmPreloadRows(snap, it) }
    }

package playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The FIFO of films waiting to preload, plus which one (if any) is active —
 * the one piece of [FilmPreloader]'s bookkeeping that both its worker
 * coroutine and a viewer's cancel/remove tap (which may run on a different
 * thread) both touch. `@Synchronized` rather than a coroutine primitive:
 * every method here is a plain, instant bookkeeping change, never a
 * suspend point.
 *
 * [hasWork] is published from inside the very same synchronized methods
 * that change [pending]/[active], not computed separately afterward — a
 * second, unsynchronized "is it empty now" read from two different callers
 * (the worker finishing an item, a fresh `enqueue` arriving) could
 * otherwise race and leave the wrong answer as the last one published,
 * which is exactly the gap that left `PreloadService` stopped with a film
 * still queued.
 */
class FilmPreloadQueue {
    private val pending = ArrayDeque<PreloadItem>()
    private var active: PreloadItem? = null

    private val _hasWork = MutableStateFlow(false)
    val hasWork: StateFlow<Boolean> = _hasWork.asStateFlow()

    /** The item downloading right now, if any. */
    val activeItem: PreloadItem?
        @Synchronized get() = active

    /** As [activeItem], just the id — what a caller checks without needing the whole item. */
    val activeId: String?
        @Synchronized get() = active?.setId

    val isEmpty: Boolean
        @Synchronized get() = pending.isEmpty() && active == null

    /** Adds [item] unless it is already queued or active. Returns whether it was added. */
    @Synchronized
    fun enqueue(item: PreloadItem): Boolean {
        if (active?.setId == item.setId || pending.any { it.setId == item.setId }) return false
        pending.addLast(item)
        publish()
        return true
    }

    /** The next item to run, marked active; `null` when nothing is waiting or one is already running. */
    @Synchronized
    fun nextToRun(): PreloadItem? {
        if (active != null) return null
        val item = pending.removeFirstOrNull() ?: return null
        active = item
        publish()
        return item
    }

    /** The active item finished on its own — done, needing space, or given up on. Clears the active slot. */
    @Synchronized
    fun clearActive() {
        active = null
        publish()
    }

    /**
     * Drops [setId] from the queue, or clears it from the active slot.
     * Returns the item if it was the active one — the worker's own signal
     * to interrupt the write in progress rather than let it keep running,
     * and the [PreloadItem.totalBytes] a caller needs to report a pause —
     * `null` for one that was merely queued, or not found at all.
     */
    @Synchronized
    fun cancel(setId: String): PreloadItem? {
        pending.removeAll { it.setId == setId }
        val current = active
        val result = if (current?.setId == setId) { active = null; current } else null
        publish()
        return result
    }

    /** Empties [pending] and returns what was in it — Android's own time limit on the foreground service pauses every waiting film, not only the active one. */
    @Synchronized
    fun drainPending(): List<PreloadItem> {
        val drained = pending.toList()
        pending.clear()
        publish()
        return drained
    }

    private fun publish() {
        _hasWork.value = pending.isNotEmpty() || active != null
    }
}

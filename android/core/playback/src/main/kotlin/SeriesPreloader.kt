package playback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** One episode to take into the cache, with what [fitsInPreloadBudget] needs to judge it. */
data class PreloadItem(val setId: String, val title: String, val totalBytes: Long)

/**
 * Takes one [PreloadItem] into the cache in full. The real implementation
 * runs a media3 `CacheWriter` over the strict `cacheDataSourceFactory`
 * beneath the one playback reads through; a test supplies a fake that only records
 * what it was asked to write.
 */
fun interface PreloadWriter {
    suspend fun write(item: PreloadItem)
}

/**
 * What a caller needs from [SeriesPreloader] — narrow enough for a test to
 * fake without a real cache, thread or network check behind it.
 */
interface SeriesPreloading {
    fun want(items: List<PreloadItem>, currentPlayingBytes: Long)

    /** One id per set this preloader has just finished taking into the cache — for a badge to follow without a rescan. */
    val heldEvents: SharedFlow<String>

    companion object {
        /** Takes nothing and reports nothing held — a constructor default for a caller that does not care. */
        val Noop: SeriesPreloading = object : SeriesPreloading {
            override fun want(items: List<PreloadItem>, currentPlayingBytes: Long) = Unit
            override val heldEvents: SharedFlow<String> = MutableSharedFlow()
        }
    }
}

/**
 * Takes the next episodes of a show into the cache while the viewer
 * watches the one before them.
 *
 * A port of the web's own `SeriesPreload`: one worker, one item
 * downloading at a time, and a later [want] replaces whatever was still
 * waiting rather than queuing behind it — a viewer who opened another
 * episode has moved on, and the ones after the old one are no longer next.
 * The set already downloading is let finish; half of it is on disk, and it
 * is usually still one of the wanted ones.
 *
 * [isHeld] and [fits] are asked fresh for every item, immediately before
 * it would be written — a set another preload already finished, or a
 * budget the last item's own write has since filled, both have to be
 * caught right there rather than at the moment [want] was called.
 */
class SeriesPreloader(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    private val writer: PreloadWriter,
    private val isHeld: suspend (PreloadItem) -> Boolean,
    private val fits: suspend (candidateBytes: Long, currentBytes: Long) -> Boolean,
    private val log: (String) -> Unit = {},
) : SeriesPreloading {
    private val wanted = Channel<List<PreloadItem>>(Channel.CONFLATED)

    @Volatile
    private var currentId: String? = null

    @Volatile
    private var currentPlayingBytes: Long = 0L

    private val _heldEvents = MutableSharedFlow<String>(extraBufferCapacity = HELD_EVENTS_BUFFER)

    override val heldEvents: SharedFlow<String> = _heldEvents.asSharedFlow()

    init {
        scope.launch(dispatcher) { runWorker() }
    }

    /**
     * What to take next, replacing whatever was still waiting.
     *
     * [currentPlayingBytes] is the whole size of the title actually
     * playing right now — reserved by [fits] against every candidate, so a
     * preload never crowds out the one title it must not touch.
     */
    override fun want(items: List<PreloadItem>, currentPlayingBytes: Long) {
        this.currentPlayingBytes = currentPlayingBytes
        wanted.trySend(items.filter { it.setId != currentId })
    }

    private suspend fun runWorker() {
        var pending: List<PreloadItem> = wanted.receive()
        while (true) {
            // A fresher want() always wins outright, checked on every pass
            // rather than only once the old list runs out — a viewer who
            // has moved to a different episode is not made to wait through
            // ones that are no longer next.
            wanted.tryReceive().getOrNull()?.let { pending = it }
            val item = pending.firstOrNull()
            if (item == null) {
                pending = wanted.receive()
                continue
            }
            pending = pending.drop(1)
            currentId = item.setId
            runItem(item)
            currentId = null
        }
    }

    @Suppress("TooGenericExceptionCaught") // a preload that fails costs nothing but the wait it was meant to save
    private suspend fun runItem(item: PreloadItem) {
        try {
            if (isHeld(item)) return
            if (!fits(item.totalBytes, currentPlayingBytes)) {
                log("preload: ${item.title} skipped (budget or network)")
                return
            }
            writer.write(item)
            _heldEvents.emit(item.setId)
            log("preload: ${item.title} held")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("preload: ${item.title} stopped: ${e.message}")
        }
    }
}

private const val HELD_EVENTS_BUFFER = 8

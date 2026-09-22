package catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import data.CatalogRepository
import data.LibraryEvents
import data.refreshSentence
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import uniffi.mediagram_core.LibraryEvent
import uniffi.mediagram_core.TitleInfo
import javax.inject.Inject

/** Refreshes the catalog on request, then groups it into shelves for the screen to render. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val repository: CatalogRepository,
    libraryEvents: LibraryEvents = LibraryEvents.None,
) : ViewModel() {

    // What [state] is built from. A cold flow handed to stateIn runs once
    // per subscription and never again, which left a viewer with no way to
    // ask the channel a second time short of killing the app. The initial
    // zero is the one automatic load the screen has always done; every
    // later value is somebody pressing for it.
    private val reloads = MutableStateFlow(0)

    // The last shelves that were built, so a reload can leave them up
    // instead of replacing a whole library with a spinner for as long as
    // the network takes. Held here rather than read back out of [state]:
    // a flow that read the StateFlow it is building would be feeding on
    // its own output. Touched by the channel reads and by [showFetched]'s
    // re-reads, both collected on the main dispatcher, so never at once —
    // and each re-read takes it only after its own suspension, so it never
    // writes back a copy another read has since replaced.
    private var lastReady: CatalogUiState.Ready? = null

    // Asks for the shelves to be built again from this device alone. Dropped
    // when nobody is watching, which is safe: watching again reads the channel.
    private val refetched = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val _published = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Once each time a newer index published from another device has been
     * read in. New media comes with no artwork or descriptions on this
     * device, so whoever holds the fetch runs it on this — the same fetch
     * Update library runs after its own read. Not said after a read the
     * button asked for (that chains its own fetch), nor after one that
     * failed (nothing new came home).
     */
    val published: SharedFlow<Unit> = _published.asSharedFlow()

    /** Re-reads the library from the channel and re-groups it. */
    fun reload() {
        reloads.update { it + 1 }
    }

    /**
     * Builds the shelves again from the catalog already on this device, for
     * artwork a fetch has just laid down. Each card's poster is looked up
     * when the shelves are built, so without this a fetch that finished after
     * the read — which is always, since it follows the read — left every new
     * poster on disk and every card showing initials until the next reload.
     * The channel is not asked, and Update stays available throughout.
     */
    fun showFetched() {
        refetched.tryEmit(Unit)
    }

    // flatMapLatest, not flatMapConcat: a second request made while the
    // first is still in flight should replace it rather than queue behind
    // it, because both would install the same snapshot.
    //
    // A newer index published from another device is one more reason to read
    // the channel, merged in beside the button. It is collected only while
    // [state] is — the screen in front — so the app listens while a viewer can
    // see the result, and a refresh that downloads the whole index is never
    // spent on a phone in a pocket. No posters are fetched on it: that stays
    // on the button, where its cost is visible.
    val state: StateFlow<CatalogUiState> = merge(
        reloads.map { false },
        // Restarted with every read, a changed library's included: a wait
        // already running is filtered to the channel it began on.
        reloads.flatMapLatest { libraryEvents.events() }.filter { it == LibraryEvent.INDEX }.map { true },
    )
        .flatMapLatest { pushed ->
            flow {
                // Loading only when there is nothing yet to keep. Every
                // later read of the channel is said over the shelves it is
                // about to replace, which are a whole library until it
                // answers.
                emit(lastReady?.copy(refreshing = true) ?: CatalogUiState.Loading)
                // The refresh is tried first and judged last. A catalog is a file on
                // this device, and it goes on being a whole library when the channel
                // cannot be reached — on a train, or while whoever uploads is midway
                // through tidying the channel. Losing the library over a failed
                // round trip would be the one failure a viewer cannot work around.
                val failure = repository.refresh().exceptionOrNull()
                val shelves = shelvesOf(runCatching { repository.sets() }.getOrDefault(emptyList()))
                val answer = when {
                    shelves.isNotEmpty() -> CatalogUiState.Ready(shelves, failure?.refreshSentence())
                    failure != null -> CatalogUiState.Failed(failure.refreshSentence())
                    else -> CatalogUiState.Empty
                }
                // Cleared, not just overwritten, when the answer is not a
                // library: a device that has emptied out has no shelves for
                // the next reload to keep up.
                lastReady = answer as? CatalogUiState.Ready
                emit(answer)
                if (pushed && failure == null) _published.tryEmit(Unit)
            }
        }
        // Beside the channel reads rather than among them: through the same
        // flatMapLatest, a fetch finishing mid-read would cancel that read.
        .let { reads -> merge(reads, refetched.mapNotNull { regrouped() }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogUiState.Loading)

    /**
     * The shelves showing, rebuilt from the catalog on disk; `null` when none
     * are showing yet, since a first read will build them with the artwork.
     * Keeps the notice and the refreshing flag of whatever is up: this only
     * changes what the cards look like.
     */
    private suspend fun regrouped(): CatalogUiState.Ready? {
        val sets = runCatching { repository.sets() }.getOrNull() ?: return null
        val kept = lastReady ?: return null
        val shelves = shelvesOf(sets).takeIf { it.isNotEmpty() } ?: return null
        return kept.copy(shelves = shelves).also { lastReady = it }
    }

    /**
     * What the index says about one title, for the screen that describes it
     * before playing it.
     *
     * Asked for on demand rather than carried in [state]: the shelves hold
     * a few hundred sets and a viewer opens one of them, so joining every
     * synopsis into the catalog would do a few hundred queries to render
     * one screen.
     */
    suspend fun titleInfo(posterKey: String): TitleInfo? = repository.titleInfo(posterKey)

    /**
     * The local file for a poster key with no set of its own to carry it —
     * a season's artwork. Asked for on demand for the same reason
     * [titleInfo] is: a wall renders a handful of these at a time, not the
     * whole library's worth.
     */
    suspend fun posterPath(posterKey: String): String? = repository.posterPath(posterKey)
}

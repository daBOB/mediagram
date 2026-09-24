package catalog

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import data.CatalogRepository
import data.LibraryEvents
import data.LibraryUpdateCoordinator
import data.LibraryUpdateKind
import data.WatchStateRepository
import data.coreSentence
import data.refreshSentence
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import model.MediaSet
import model.forKidsProfile
import uniffi.mediagram_core.LibraryEvent
import uniffi.mediagram_core.TitleInfo
import javax.inject.Inject

/** Keeps shelf presentation separate from the awaited refresh and artwork operation. */
@HiltViewModel
class CatalogViewModel
    @Inject
    constructor(
        private val repository: CatalogRepository,
        private val watchState: WatchStateRepository,
        private val updates: LibraryUpdateCoordinator,
        libraryEvents: LibraryEvents = LibraryEvents.None,
    ) : ViewModel() {
        private val catalog = MutableStateFlow<CatalogUiState>(CatalogUiState.Loading)
        private var lastReady: CatalogUiState.Ready? = null
        private var manualUpdate: Job? = null

        /** The sets behind the last Ready state, before any profile's filter. */
        private var lastSets: List<MediaSet> = emptyList()

        /**
         * The marked-by-hand set when the chosen profile is a kids profile,
         * `null` otherwise. Distinct, so progress updates — which also move
         * `snapshot` — do not regroup the shelves.
         */
        private val kidsFilter: Flow<Set<String>?> =
            combine(watchState.profiles, watchState.chosenProfileId, watchState.snapshot) { _, _, _ -> currentKids() }
                .distinctUntilChanged()

        /** [currentKids] as a synchronous read, for a value computed outside collection. */
        private fun currentKids(): Set<String>? {
            val chosen = watchState.chosenProfileId.value
            val kids = watchState.profiles.value.firstOrNull { it.id == chosen }?.kids == true
            return if (kids) watchState.snapshot.value.kids.toSet() else null
        }

        /**
         * The channel read and its reaction to library-update events — kept
         * exactly as it read before kids profiles existed, so a gap in
         * collection still means refresh-on-resubscribe. Not filtered: that
         * is [state]'s job, applied over this pipe's always-current [value][StateFlow.value]
         * rather than baked into what this one caches.
         */
        private val unfiltered: StateFlow<CatalogUiState> =
            channelFlow {
                launch { refresh(LibraryUpdateKind.Read) }
                launch {
                    libraryEvents.events().collect { event ->
                        if (event == LibraryEvent.INDEX) refresh(LibraryUpdateKind.Published)
                    }
                }
                combine(catalog, updates.refreshing, watchState.snapshot) { shown, refreshing, watch ->
                    when {
                        shown is CatalogUiState.Ready -> shown.copy(refreshing = refreshing, watch = watch)
                        refreshing -> CatalogUiState.Loading
                        else -> shown
                    }
                }.collect { send(it) }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogUiState.Loading)

        /**
         * [unfiltered], projected for whoever is chosen right now.
         *
         * The picker takes the library out of composition while it is shown,
         * and [unfiltered] itself may have stopped producing after five
         * seconds with nobody collecting it — so a profile switch made while
         * nothing was collecting [state] must not leave a stale [unfiltered]
         * value, filtered for whoever was chosen before, as the next
         * collector's first item. [value] recomputes the filter from
         * [unfiltered]'s own always-current value on every read instead of
         * caching it, so there is nothing here to go stale.
         */
        @OptIn(kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi::class)
        val state: StateFlow<CatalogUiState> =
            object : StateFlow<CatalogUiState> {
                override val value: CatalogUiState
                    get() = project(unfiltered.value, currentKids())

                override val replayCache: List<CatalogUiState>
                    get() = listOf(value)

                override suspend fun collect(collector: FlowCollector<CatalogUiState>): Nothing {
                    combine(unfiltered, kidsFilter) { shown, kids -> project(shown, kids) }
                        .distinctUntilChanged()
                        .collect(collector)
                    error("unreachable: a StateFlow-backed combine never completes")
                }
            }

        /** One place the filter applies: every wall and title page on the phone is built from these shelves. */
        private fun project(
            shown: CatalogUiState,
            kids: Set<String>?,
        ): CatalogUiState =
            when {
                shown is CatalogUiState.Ready && kids != null -> {
                    val shelves = shelvesOf(forKidsProfile(lastSets, kids))
                    if (shelves.isEmpty()) CatalogUiState.KidsEmpty else shown.copy(shelves = shelves)
                }
                else -> shown
            }

        /** Settings changed the library; its first read is separate from enrichment requests. */
        fun reload() {
            viewModelScope.launch { refresh(LibraryUpdateKind.Read) }
        }

        fun update() {
            if (manualUpdate?.isActive == true) return
            manualUpdate = viewModelScope.launch { refresh(LibraryUpdateKind.Manual) }
        }

        /** Rebuilds local presentation without changing the independently owned refresh flag. */
        fun showFetched() {
            viewModelScope.launch { regrouped() }
        }

        private suspend fun refresh(kind: LibraryUpdateKind) {
            updates.update(kind, ::readCatalog, ::regrouped)
        }

        private suspend fun readCatalog(refreshed: Result<Int>) {
            val answer =
                try {
                    val failure = refreshed.exceptionOrNull()
                    failure?.let { Log.w("Catalog", "could not refresh the library", it) }
                    val sets = repository.sets()
                    lastSets = sets
                    val shelves = shelvesOf(sets)
                    when {
                        shelves.isNotEmpty() -> CatalogUiState.Ready(shelves, notice = failure?.refreshSentence())
                        failure != null -> CatalogUiState.Failed(failure.refreshSentence())
                        else -> CatalogUiState.Empty
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    Log.w("Catalog", "could not read the library", e)
                    val notice = e.coreSentence() ?: "Could not read the library. Try again."
                    lastReady?.copy(notice = notice) ?: CatalogUiState.Failed(notice)
                }
            show(answer)
        }

        private suspend fun regrouped() {
            try {
                val sets = repository.sets()
                val shelves = shelvesOf(sets)
                val kept = lastReady ?: return
                if (shelves.isNotEmpty()) {
                    lastSets = sets
                    show(kept.copy(shelves = shelves))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                Log.w("Catalog", "could not reread the library after enrichment", e)
                val notice = e.coreSentence() ?: "Could not read the library. Try again."
                show(lastReady?.copy(notice = notice) ?: CatalogUiState.Failed(notice))
            }
        }

        private fun show(answer: CatalogUiState) {
            lastReady = answer as? CatalogUiState.Ready
            catalog.value = answer
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

        fun createList(name: String) {
            writeCollection("create") { watchState.createList(name) != null }
        }

        fun renameList(
            id: String,
            name: String,
        ) {
            writeCollection("rename") { watchState.renameList(id, name) }
        }

        fun deleteList(id: String) {
            writeCollection("delete") { watchState.deleteList(id) }
        }

        fun setInList(
            id: String,
            setId: String,
            included: Boolean,
        ) {
            writeCollection("update") { watchState.setInList(id, setId, included) }
        }

        /** Repository snapshots acknowledge successful writes; failures leave those snapshots intact. */
        private fun writeCollection(
            verb: String,
            write: suspend () -> Boolean,
        ) {
            viewModelScope.launch {
                val saved =
                    try {
                        write()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (
                        @Suppress("TooGenericExceptionCaught") e: Exception,
                    ) {
                        false
                    }
                val failure = "Could not $verb the collection. Please try again."
                val kept = lastReady
                if (!saved) {
                    show(kept?.copy(notice = failure) ?: CatalogUiState.Failed(failure))
                } else if (kept != null && kept.notice == failure) {
                    show(kept.copy(notice = null))
                }
            }
        }
    }

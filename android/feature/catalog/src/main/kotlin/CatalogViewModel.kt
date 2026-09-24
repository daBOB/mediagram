package catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import data.CatalogRepository
import data.LibraryEvents
import data.LibraryUpdateCoordinator
import data.LibraryUpdateKind
import data.WatchStateRepository
import data.refreshSentence
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

        // Listening follows visible state collection. An explicitly requested update
        // belongs to viewModelScope and survives the composition that submitted it.
        val state: StateFlow<CatalogUiState> =
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
                    val shelves = shelvesOf(repository.sets())
                    val failure = refreshed.exceptionOrNull()
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
                    lastReady?.copy(notice = e.refreshSentence()) ?: CatalogUiState.Failed(e.refreshSentence())
                }
            show(answer)
        }

        private suspend fun regrouped() {
            try {
                val shelves = shelvesOf(repository.sets())
                val kept = lastReady ?: return
                if (shelves.isNotEmpty()) show(kept.copy(shelves = shelves))
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                show(lastReady?.copy(notice = e.refreshSentence()) ?: CatalogUiState.Failed(e.refreshSentence()))
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

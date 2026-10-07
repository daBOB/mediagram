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
import data.orDefault
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import model.MediaSet
import model.TitleCredits
import model.forKidsProfile
import playback.FilmPreloading
import playback.HeldSetsQuery
import playback.SeriesPreloading
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
        private val heldSets: HeldSetsQuery = HeldSetsQuery.Noop,
        seriesPreloader: SeriesPreloading = SeriesPreloading.Noop,
        filmPreloader: FilmPreloading = FilmPreloading.Noop,
    ) : ViewModel() {
        private val catalog = MutableStateFlow<CatalogUiState>(CatalogUiState.Loading)
        private var lastReady: CatalogUiState.Ready? = null
        private var manualUpdate: Job? = null

        /** The sets behind the last Ready state, before any profile's filter. */
        private var lastSets: List<MediaSet> = emptyList()

        /** What a kids profile is shown by: the hand marks by age, and its own limit. */
        private data class KidsView(
            val marks: Map<String, Int>,
            val limit: Int,
        )

        /**
         * The chosen kid's [KidsView], `null` for a grown-up. Distinct, so
         * progress updates — which also move `snapshot` — do not regroup the
         * shelves; a limit changed in Manage or by sync does.
         */
        private val kidsFilter: Flow<KidsView?> =
            combine(watchState.chosenProfile, watchState.snapshot) { _, _ -> currentKids() }
                .distinctUntilChanged()

        /** [kidsFilter] as a synchronous read, for a value computed outside collection. */
        private fun currentKids(): KidsView? {
            val kid = watchState.chosenProfile.value?.takeIf { it.kids } ?: return null
            return KidsView(watchState.snapshot.value.kidsMarks, kid.kidsLimit)
        }

        /**
         * Whether the chosen profile is a kids profile — the title page's own
         * reason to hide "Make editor's choice" there: a household mark like
         * the pin is not a kids profile's to make.
         */
        val kidsProfile: StateFlow<Boolean> =
            watchState.chosenProfile
                .map { it?.kids == true }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        /**
         * Adds or removes [setId] from "My List", for a control that already
         * says which way — the home cover's pill and every card's own toggle.
         */
        fun setWatchlisted(
            setId: String,
            listed: Boolean,
        ) {
            writeState("Could not confirm the Watchlist update. Check it and try again.") {
                watchState.setWatchlisted(setId, listed)
                true
            }
        }

        /** A title page's "My List" button: which way it goes is read from the snapshot at the tap. */
        fun toggleWatchlist(setId: String) {
            setWatchlisted(setId, setId !in watchState.snapshot.value.watchlist)
        }

        /**
         * A title page's editor's-choice button, pinning [setId] or taking
         * the pin off it. Refused for a kids profile here as well as by the
         * button's absence: the pin is the household's, not a kid's, to make.
         */
        fun toggleEditorsChoice(setId: String) {
            if (currentKids() != null) return
            val marked = watchState.snapshot.value.editorsChoice != setId
            writeState("Could not confirm the editor's choice update. Check it and try again.") {
                watchState.setEditorsChoice(setId, marked)
                true
            }
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
                // A preload that finishes a title is folded into the shelves
                // already up, so its "offline" badge shows without a rescan.
                launch { seriesPreloader.heldEvents.collect(::heldEventApplied) }
                launch { filmPreloader.heldEvents.collect(::heldEventApplied) }
                // A film a viewer removed drops its badge the same way — the
                // one case heldEventApplied's own event never covers, since
                // nothing else in the app ever un-holds a title once it is on
                // disk.
                launch { filmPreloader.unheldEvents.collect(::heldEventRemoved) }
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
            kids: KidsView?,
        ): CatalogUiState =
            when {
                shown is CatalogUiState.Ready && kids != null -> {
                    val shelves = shelvesOf(forKidsProfile(lastSets, kids.marks, kids.limit))
                    if (!shelves.hasContent()) CatalogUiState.KidsEmpty(kids.limit) else shown.copy(shelves = shelves)
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
                        shelves.hasContent() -> CatalogUiState.Ready(shelves, heldIds = heldIdsOf(shelves), notice = failure?.refreshSentence())
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
                if (shelves.hasContent()) {
                    lastSets = sets
                    show(kept.copy(shelves = shelves, heldIds = heldIdsOf(shelves)))
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

        /** Which of these sets this device holds in full, asked of the cache alone. */
        private suspend fun heldIdsOf(shelves: List<Shelf>): Set<String> =
            heldSets.heldIds(allSetsById(shelves).values.map { it.setId to it.totalBytes })

        private fun heldEventApplied(setId: String) {
            val kept = lastReady ?: return
            if (setId !in kept.heldIds) show(kept.copy(heldIds = kept.heldIds + setId))
        }

        private fun heldEventRemoved(setId: String) {
            val kept = lastReady ?: return
            if (setId in kept.heldIds) show(kept.copy(heldIds = kept.heldIds - setId))
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
         * A title's cast and crew, for the Cast tab that only appears once
         * credits arrive and name somebody — the same on-demand shape
         * [titleInfo] already follows, for the same reason.
         */
        suspend fun titleCredits(key: String): TitleCredits = repository.titleCredits(key)

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

        /** Continue's "Mark finished": the wall redraws from the snapshot, so the title simply leaves it. */
        fun markFinished(setId: String) {
            writeState("Could not mark that title finished. Please try again.") {
                watchState.markFinished(setId)
                true
            }
        }

        private fun writeCollection(
            verb: String,
            write: suspend () -> Boolean,
        ) = writeState("Could not $verb the collection. Please try again.", write)

        /** Repository snapshots acknowledge successful writes; failures leave those snapshots intact. */
        private fun writeState(
            failure: String,
            write: suspend () -> Boolean,
        ) {
            viewModelScope.launch {
                val saved = orDefault(false, "catalog write") { write() }
                val kept = lastReady
                if (!saved) {
                    show(kept?.copy(notice = failure) ?: CatalogUiState.Failed(failure))
                } else if (kept != null && kept.notice == failure) {
                    show(kept.copy(notice = null))
                }
            }
        }
    }

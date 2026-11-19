package data

import data.settings.ArtworkFetchMark
import data.settings.InMemoryArtworkFetchMark
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import model.MediaSet
import uniffi.mediagram_core.FetchProgress
import uniffi.mediagram_core.FetchReport
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Why a library update runs: the player opened or its library changed
 * ([Read]), the viewer asked ([Manual]), or another device published an
 * index ([Published]).
 */
enum class LibraryUpdateKind { Read, Manual, Published }

/**
 * One read of the sets installed on this device, carrying the sentence of a
 * refresh that could not reach the channel, if the last one could not.
 *
 * Not a data class: every read is news, even of the same sets, because
 * whoever shows it asks the cache again what it holds — eviction changes
 * that without an event.
 */
class InstalledLibrary(
    val sets: Result<List<MediaSet>>,
    val refusal: String? = null,
)

/** What the library update is doing, and what its last fetch had to say until it is dismissed. */
data class LibraryUpdateState(
    val reading: Boolean = false,
    val fetching: Boolean = false,
    val progress: FetchProgress? = null,
    val report: FetchReport? = null,
    val error: String? = null,
)

/**
 * The library update: the channel's newest index, then the artwork it lacks,
 * then the installed sets read again — one update at a time, so a read never
 * lands after the read of a newer snapshot.
 *
 * Whether artwork is fetched: a [LibraryUpdateKind.Read] when its refresh
 * installed a newer index, or reached the channel and found the installed
 * index one no fetch has finished for yet ([ArtworkFetchMark]) — the index a
 * device asleep at its publishing, or killed mid-fetch, installed bare; never
 * on every start, since a fetch plans over every title. A
 * [LibraryUpdateKind.Published] fetches unless the channel refused, and a
 * [LibraryUpdateKind.Manual] always, even offline, to fill the held catalog's
 * gaps. A fetch that finished marks the installed index done. Only a manual
 * fetch reports.
 */
@Singleton
class LibraryUpdate
    @Inject
    constructor(
        private val repository: CatalogRepository,
        private val enrichment: CatalogEnrichmentFetcher,
        private val fetchMark: ArtworkFetchMark = InMemoryArtworkFetchMark(),
    ) {
        private val updating = Mutex()
        private val reading = MutableStateFlow(false)
        private val installed = MutableStateFlow<InstalledLibrary?>(null)

        /** The newest read of the installed sets: after each refresh, and after a fetch that laid artwork down. `null` before the first. */
        val library: StateFlow<InstalledLibrary?> = installed.asStateFlow()

        /** Its value is worked out on every read, so it is never behind [library]. */
        @OptIn(ExperimentalForInheritanceCoroutinesApi::class)
        val state: StateFlow<LibraryUpdateState> =
            object : StateFlow<LibraryUpdateState> {
                override val value: LibraryUpdateState
                    get() = stateOf(reading.value, enrichment.state.value)

                override val replayCache: List<LibraryUpdateState>
                    get() = listOf(value)

                override suspend fun collect(collector: FlowCollector<LibraryUpdateState>): Nothing {
                    combine(reading, enrichment.state) { read, fetch -> stateOf(read, fetch) }
                        .distinctUntilChanged()
                        .collect(collector)
                    error("unreachable: a StateFlow-backed combine never completes")
                }
            }

        suspend fun update(kind: LibraryUpdateKind) =
            updating.withLock {
                val outcome =
                    try {
                        reading.value = true
                        // A refresh can mean downloading the whole index, so a first
                        // start shows what is installed while it runs.
                        if (kind == LibraryUpdateKind.Read && installed.value == null) showInstalled()
                        repository.refresh().also { installed.value = InstalledLibrary(readSets(), it.refusal()) }
                    } finally {
                        reading.value = false
                    }
                val published = repository.publishedAt()
                val fetches =
                    when (kind) {
                        LibraryUpdateKind.Read ->
                            outcome == RefreshOutcome.Updated ||
                                (outcome == RefreshOutcome.AlreadyCurrent && published != null && published != fetchMark.fetchedFor())
                        LibraryUpdateKind.Published -> outcome !is RefreshOutcome.Refused
                        LibraryUpdateKind.Manual -> true
                    }
                if (!fetches) return@withLock
                val report = enrichment.fetch(quiet = kind != LibraryUpdateKind.Manual)
                if (report != null && published != null) fetchMark.mark(published)
                if (report != null && (report.postersFetched > 0u || report.backdropsFetched > 0u)) {
                    installed.value = InstalledLibrary(readSets(), outcome.refusal())
                }
            }

        /**
         * Lets go of the last read after Start over or Sign out, so no screen shows
         * the previous account's library and the next Read shows what is installed.
         */
        fun forget() {
            installed.value = null
        }

        /** Clears what the last fetch said, once the viewer has read it. */
        fun dismissResult() = enrichment.dismissResult()

        /** Quiet when nothing is installed or it cannot be read: the read after the refresh is the one that reports. */
        private suspend fun showInstalled() {
            readSets().getOrNull()?.takeIf { it.isNotEmpty() }?.let { installed.value = InstalledLibrary(Result.success(it)) }
        }

        private suspend fun readSets(): Result<List<MediaSet>> = runCatching { repository.sets() }.onFailure { if (it is CancellationException) throw it }

        private fun RefreshOutcome.refusal(): String? = (this as? RefreshOutcome.Refused)?.reason

        private fun stateOf(
            reading: Boolean,
            fetch: CatalogEnrichmentState,
        ) = LibraryUpdateState(reading, fetch.running, fetch.progress, fetch.report, fetch.error)
    }

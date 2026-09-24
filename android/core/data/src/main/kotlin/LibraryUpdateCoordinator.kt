package data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Initial/settings reads do not fetch; manual updates fetch even when the channel is offline. */
enum class LibraryUpdateKind { Read, Manual, Published }

/** Serializes refresh and local reads; enrichment that fetched posters triggers an artwork reread without UI collectors. */
@Singleton
class LibraryUpdateCoordinator
    @Inject
    constructor(
        private val repository: CatalogRepository,
        private val enrichment: CatalogEnrichmentFetcher,
    ) {
        private val updating = Mutex()
        private val reading = MutableStateFlow(false)
        val refreshing = reading.asStateFlow()

        suspend fun update(
            kind: LibraryUpdateKind,
            readCatalog: suspend (Result<Int>) -> Unit,
            showArtwork: suspend () -> Unit,
        ) = updating.withLock {
            val refreshed =
                try {
                    reading.value = true
                    val result =
                        try {
                            repository.refresh()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (
                            @Suppress("TooGenericExceptionCaught") e: Exception,
                        ) {
                            Result.failure(e)
                        }
                    (result.exceptionOrNull() as? CancellationException)?.let { throw it }
                    readCatalog(result)
                    result
                } finally {
                    reading.value = false
                }
            if (kind == LibraryUpdateKind.Read || (kind == LibraryUpdateKind.Published && refreshed.isFailure)) return@withLock
            val report = enrichment.fetch(quiet = kind == LibraryUpdateKind.Published)
            if (report != null && report.postersFetched > 0u) showArtwork()
        }
    }

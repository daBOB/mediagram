package catalog

import data.BackdropWidth
import data.CatalogEnrichmentFetcher
import data.CoreClient
import data.LibraryUpdateCoordinator
import data.LibraryUpdateKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import settings.InMemoryTmdbSettings
import uniffi.mediagram_core.FetchReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryUpdateCoordinatorTest {
    @Test
    fun immediateRefreshAndFetchCompleteWithoutAnyUiObserver() =
        runTest {
            val fixture = UpdateFixture()
            fixture.run()
            assertEquals(listOf("read", "fetch", "artwork"), fixture.steps)
            assertEquals(1, fixture.repository.refreshes)
            assertFalse(fixture.coordinator.refreshing.value)
            assertFalse(fixture.enrichment.state.value.running)
        }

    @Test
    fun fetchingWaitsForRefreshAndArtworkIsReadAfterItIsWritten() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val fixture = UpdateFixture().apply { repository.refreshGate = gate }
            val update = launch { fixture.run() }
            runCurrent()
            assertTrue(fixture.coordinator.refreshing.value)
            assertTrue(fixture.steps.isEmpty())
            gate.complete(Unit)
            update.join()
            assertEquals(listOf("read", "fetch", "artwork"), fixture.steps)
            assertTrue(fixture.artworkVisible)
        }

    @Test
    fun aManualRefreshFailureStillFillsGapsInTheHeldCatalog() =
        runTest {
            val fixture = UpdateFixture(FakeCatalogRepository(movies = 1, refreshFails = true))
            fixture.run()
            assertTrue(fixture.refreshFailed)
            assertEquals(listOf("read", "fetch", "artwork"), fixture.steps)
            assertEquals(fixture.report, fixture.enrichment.state.value.report)
        }

    @Test
    fun aFailedPublishedReadDoesNotFetch() =
        runTest {
            val fixture = UpdateFixture(FakeCatalogRepository(movies = 1, refreshFails = true))
            fixture.run(LibraryUpdateKind.Published)
            assertEquals(listOf("read"), fixture.steps)
            assertNull(fixture.enrichment.state.value.report)
        }

    @Test
    fun aPublishedReadFetchesAndRegroupsQuietly() =
        runTest {
            val fixture = UpdateFixture()
            fixture.run(LibraryUpdateKind.Published)
            assertEquals(listOf("read", "fetch", "artwork"), fixture.steps)
            assertTrue(fixture.artworkVisible)
            assertNull(fixture.enrichment.state.value.report)
        }

    @Test
    fun aReadWithoutAnUpdateDoesNotSpendArtwork() =
        runTest {
            val fixture = UpdateFixture()
            fixture.run(LibraryUpdateKind.Read)
            assertEquals(listOf("read"), fixture.steps)
        }

    @Test
    fun cancellationDuringRefreshDoesNotFetchAndReleasesTheNextUpdate() =
        runTest {
            val fixture = UpdateFixture().apply { repository.refreshGate = CompletableDeferred() }
            val cancelled = launch { fixture.run() }
            runCurrent()
            cancelled.cancelAndJoin()
            assertFalse(fixture.coordinator.refreshing.value)
            assertTrue(fixture.steps.isEmpty())
            fixture.repository.refreshGate = null
            fixture.run()
            assertEquals(listOf("read", "fetch", "artwork"), fixture.steps)
        }

    @Test
    fun cancellationDuringFetchDoesNotRegroupAndClearsTheRunningState() =
        runTest {
            val fixture = UpdateFixture().apply { fetchGate = CompletableDeferred() }
            val cancelled = launch { fixture.run() }
            runCurrent()
            assertTrue(fixture.enrichment.state.value.running)
            cancelled.cancelAndJoin()
            assertEquals(listOf("read", "fetch"), fixture.steps)
            assertFalse(fixture.enrichment.state.value.running)
            assertNull(fixture.enrichment.state.value.error)
            fixture.fetchGate = null
            fixture.run()
            assertTrue(fixture.artworkVisible)
        }

    @Test
    fun aSecondUpdateCannotReadTheCatalogDuringTheFirstFetch() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val fixture = UpdateFixture().apply { fetchGate = gate }
            val first = launch { fixture.run() }
            runCurrent()
            val second = launch { fixture.run() }
            runCurrent()
            assertEquals(1, fixture.repository.refreshes)
            gate.complete(Unit)
            first.join()
            second.join()
            assertEquals(2, fixture.repository.refreshes)
            assertEquals(listOf("read", "fetch", "artwork", "read", "fetch", "artwork"), fixture.steps)
        }
}

private class UpdateFixture(
    val repository: FakeCatalogRepository = FakeCatalogRepository(movies = 1),
) {
    val report = FetchReport(1u, 0u, 0u, 0u, 1u, 0u, 0u, 0u)
    val steps = mutableListOf<String>()
    var fetchGate: CompletableDeferred<Unit>? = null
    var refreshFailed = false
    var artworkVisible = false
    private val core =
        object : CoreClient by CatalogCore() {
            override suspend fun fetchMissing(
                tmdbKey: String,
                language: String,
                backdropWidth: Int,
            ): FetchReport {
                steps += "fetch"
                fetchGate?.await()
                repository.postersArrived = true
                return report
            }
        }
    val enrichment = CatalogEnrichmentFetcher(CatalogCoreProvider(core), InMemoryTmdbSettings(), BackdropWidth { 780 })
    val coordinator = LibraryUpdateCoordinator(repository, enrichment)

    suspend fun run(kind: LibraryUpdateKind = LibraryUpdateKind.Manual) {
        enrichment.saveKey("test-key")
        coordinator.update(kind, { result ->
            steps += "read"
            refreshFailed = result.isFailure
            repository.sets()
        }, {
            steps += "artwork"
            artworkVisible = repository.sets().all { it.posterPath != null }
        })
    }
}

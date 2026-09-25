package system

import data.CatalogEnrichmentFetcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import settings.InMemoryTmdbSettings
import uniffi.mediagram_core.FetchReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogEnrichmentFetcherTest {
    @Test
    fun anOverlappingFetchSkipsImmediatelyWithoutJoiningOrClearingActiveProgress() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val report = FetchReport(1u, 2u, 0u, 0u, 3u, 4u, 5u, 6u)
            val core = FakeCore(report = report, gate = gate)
            val enrichment = CatalogEnrichmentFetcher(FakeCoreProvider(core), InMemoryTmdbSettings().apply { write("key") })
            val accepted = async { enrichment.fetch() }
            runCurrent()

            assertNull(enrichment.fetch())
            assertFalse(accepted.isCompleted)
            assertTrue(enrichment.state.value.running)
            assertEquals(1, core.fetchCalls)
            gate.complete(Unit)
            assertEquals(report, accepted.await())
            assertEquals(report, enrichment.state.value.report)
            assertFalse(enrichment.state.value.running)
        }

    @Test
    fun aQuietFailureReturnsNullWithoutReplacingTheDisplayedResult() =
        runTest {
            val core = FakeCore(report = FetchReport(1u, 2u, 0u, 0u, 3u, 4u, 5u, 6u))
            val enrichment = CatalogEnrichmentFetcher(FakeCoreProvider(core), InMemoryTmdbSettings().apply { write("key") })
            val displayed = enrichment.fetch()
            core.failure = IllegalStateException("unavailable")

            assertNull(enrichment.fetch(quiet = true))

            assertEquals(displayed, enrichment.state.value.report)
            assertNull(enrichment.state.value.error)
            assertFalse(enrichment.state.value.running)
        }
}

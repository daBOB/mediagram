package system

import data.CatalogEnrichmentFetcher
import data.HomeCacheServer
import data.settings.InMemoryTmdbSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import testing.FakeCore
import testing.FakeCoreProvider
import uniffi.mediagram_core.FetchProgress
import uniffi.mediagram_core.FetchReport
import uniffi.mediagram_core.FetchStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
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

    @Test
    fun aRunUnderWayShowsHowFarTheCoreHasGotAndClearsItWhenDone() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val core = FakeCore(gate = gate)
            val enrichment = CatalogEnrichmentFetcher(FakeCoreProvider(core), InMemoryTmdbSettings().apply { write("key") })
            val run = async { enrichment.fetch() }
            runCurrent()

            val looking = FetchProgress(FetchStep.LOOKING_UP, 12u, 640u, 4_000uL)
            core.progress = looking
            advanceTimeBy(1_000)
            assertEquals(looking, enrichment.state.value.progress)

            gate.complete(Unit)
            run.await()
            assertNull(enrichment.state.value.progress)
        }

    @Test
    fun theCoreFetchRunsOffTheCallersThreadSoTheScreenAndItsProgressPollKeepGoing() =
        runTest {
            val core = FakeCore()
            CatalogEnrichmentFetcher(FakeCoreProvider(core), InMemoryTmdbSettings().apply { write("key") }).fetch()

            assertNotSame(Thread.currentThread(), core.fetchThread)
        }

    @Test
    fun noKeyWithAServerRunsKeyless() =
        runTest {
            val core = FakeCore()
            val enrichment = CatalogEnrichmentFetcher(FakeCoreProvider(core), InMemoryTmdbSettings(), homeCache = HomeCacheServer { true })

            enrichment.fetch()

            assertEquals(1, core.fetchCalls)
            assertEquals("", core.lastKey)
            assertFalse(enrichment.state.value.hasKey)
            assertTrue(enrichment.state.value.viaHomeCache)
        }

    @Test
    fun noKeyWithoutAServerSaysSoAsToday() =
        runTest {
            val core = FakeCore()
            val settings = InMemoryTmdbSettings().apply { write("key") }
            val enrichment = CatalogEnrichmentFetcher(FakeCoreProvider(core), settings, homeCache = HomeCacheServer { false })
            enrichment.refreshKeyStatus()
            settings.clear()

            assertNull(enrichment.fetch())

            assertEquals(0, core.fetchCalls)
            assertEquals("No TMDB key is stored.", enrichment.state.value.error)
            assertFalse(enrichment.state.value.viaHomeCache)
        }
}

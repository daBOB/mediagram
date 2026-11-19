package system

import data.CatalogEnrichmentFetcher
import data.HomeCacheServer
import data.settings.InMemoryTmdbSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import testing.FakeCore
import testing.FakeCoreProvider
import uniffi.mediagram_core.CoreException
import uniffi.mediagram_core.FetchProgress
import uniffi.mediagram_core.FetchReport
import uniffi.mediagram_core.FetchStep
import java.util.Locale
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
            // The core call runs on the test's clock, so `runCurrent` has reached it before the asserts.
            val dispatcher = StandardTestDispatcher(testScheduler)
            val settings = InMemoryTmdbSettings().apply { write("key") }
            val enrichment = CatalogEnrichmentFetcher(FakeCoreProvider(core), settings, dispatcher = dispatcher)
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
            val report = FetchReport(3u, 0u, 0u, 0u, 3u, 0u, 0u, 0u)
            val core = FakeCore(report = report)
            val enrichment = CatalogEnrichmentFetcher(FakeCoreProvider(core), InMemoryTmdbSettings(), homeCache = linkedHomeCache())

            assertEquals(report, enrichment.fetch())

            assertEquals(1, core.fetchCalls)
            assertEquals("", core.lastKey)
            assertEquals(report, enrichment.state.value.report)
            assertFalse(enrichment.state.value.hasKey)
            assertTrue(enrichment.state.value.viaHomeCache)
        }

    @Test
    fun noKeyWithoutAServerSaysSoAsToday() =
        runTest {
            val core = FakeCore()
            val settings = InMemoryTmdbSettings().apply { write("key") }
            val enrichment = CatalogEnrichmentFetcher(FakeCoreProvider(core), settings, homeCache = HomeCacheServer())
            enrichment.refreshKeyStatus()
            settings.clear()

            assertNull(enrichment.fetch())

            assertEquals(0, core.fetchCalls)
            assertEquals("No TMDB key is stored.", enrichment.state.value.error)
            assertFalse(enrichment.state.value.viaHomeCache)
        }

    @Test
    fun aKeylessRunTheServerCouldNotAnswerEndsAsWithoutAServer() =
        runTest {
            val core = FakeCore(report = FetchReport(0u, 4u, 0u, 0u, 0u, 4u, 1u, 0u))
            val settings = InMemoryTmdbSettings().apply { write("key") }
            val enrichment = CatalogEnrichmentFetcher(FakeCoreProvider(core), settings, homeCache = linkedHomeCache())
            enrichment.refreshKeyStatus()
            settings.clear()

            assertNull(enrichment.fetch())

            assertEquals(1, core.fetchCalls)
            assertNull(enrichment.state.value.report)
            assertEquals("No TMDB key is stored.", enrichment.state.value.error)
            assertFalse(enrichment.state.value.hasKey)
        }

    @Test
    fun theUpdateNoteFollowsTheServerWithoutAFetch() =
        runTest {
            val home = HomeCacheServer()
            val enrichment = CatalogEnrichmentFetcher(FakeCoreProvider(FakeCore()), InMemoryTmdbSettings(), homeCache = home)
            backgroundScope.launch { enrichment.followHomeCache() }
            runCurrent()
            assertFalse(enrichment.state.value.viaHomeCache)

            home.linked("http://192.168.0.240:7788")
            runCurrent()
            assertTrue(enrichment.state.value.viaHomeCache)

            home.linked(null)
            runCurrent()
            assertFalse(enrichment.state.value.viaHomeCache)
        }

    @Test
    fun fetchingWithNoKeyStoredDoesNothing() =
        runTest {
            val core = FakeCore()
            val enrichment = CatalogEnrichmentFetcher(FakeCoreProvider(core), InMemoryTmdbSettings(), dispatcher = Dispatchers.Unconfined)

            enrichment.fetch()

            assertNull(core.lastKey)
            assertNull(enrichment.state.value.report)
        }

    /**
     * The defect this closes: the fallback language used to be the constant
     * `en-US`, so a library with nothing to say about its own language was
     * described to its owner in a language they may not read. The device
     * knows better, and is the only thing here that does.
     */
    @Test
    fun theFallbackLanguageIsTheDevicesOwn() =
        runTest {
            val core = FakeCore()
            val enrichment = CatalogEnrichmentFetcher(FakeCoreProvider(core), InMemoryTmdbSettings().apply { write("a-fake-key") }, dispatcher = Dispatchers.Unconfined)

            enrichment.fetch()

            assertEquals(Locale.getDefault().toLanguageTag(), core.lastLanguage)
        }

    /** A rejected key is named as such — never quoted back, and never mistaken for a network fault. */
    @Test
    fun aRejectedKeyNamesTheKeyRatherThanTheNetwork() =
        runTest {
            val core = FakeCore(failure = CoreException.NotAuthorized("That key was not accepted."))
            val enrichment = CatalogEnrichmentFetcher(FakeCoreProvider(core), InMemoryTmdbSettings().apply { write("a-wrong-key") }, dispatcher = Dispatchers.Unconfined)

            enrichment.fetch()

            assertEquals("That key was not accepted.", enrichment.state.value.error)
            assertNull(enrichment.state.value.report)
        }

    @Test
    fun dismissingAResultClearsItWithoutTouchingWhetherAKeyIsStored() =
        runTest {
            val core = FakeCore(report = FetchReport(1u, 0u, 0u, 0u, 0u, 0u, 0u, 0u))
            val enrichment = CatalogEnrichmentFetcher(FakeCoreProvider(core), InMemoryTmdbSettings().apply { write("a-fake-key") }, dispatcher = Dispatchers.Unconfined)
            enrichment.fetch()

            enrichment.dismissResult()

            assertNull(enrichment.state.value.report)
            assertTrue(enrichment.state.value.hasKey)
        }

    /** The fetch new media brings is nobody's request: it does its work and says nothing. */
    @Test
    fun aQuietFetchFillsInWithoutAReport() =
        runTest {
            val core = FakeCore(report = FetchReport(2u, 7u, 0u, 0u, 5u, 1u, 0u, 0u))
            val enrichment = CatalogEnrichmentFetcher(FakeCoreProvider(core), InMemoryTmdbSettings().apply { write("a-fake-key") }, dispatcher = Dispatchers.Unconfined)

            enrichment.fetch(quiet = true)

            assertEquals("a-fake-key", core.lastKey)
            assertNull(enrichment.state.value.report)
            assertNull(enrichment.state.value.error)
            assertFalse(enrichment.state.value.running)
        }

    /** A result the viewer has not dismissed yet is theirs; a quiet fetch neither clears nor replaces it. */
    @Test
    fun aQuietFetchLeavesAResultStillOnScreen() =
        runTest {
            val core = FakeCore(report = FetchReport(1u, 0u, 0u, 0u, 0u, 0u, 0u, 0u))
            val enrichment = CatalogEnrichmentFetcher(FakeCoreProvider(core), InMemoryTmdbSettings().apply { write("a-fake-key") }, dispatcher = Dispatchers.Unconfined)
            enrichment.fetch()

            enrichment.fetch(quiet = true)

            assertEquals(FetchReport(1u, 0u, 0u, 0u, 0u, 0u, 0u, 0u), enrichment.state.value.report)
        }

    @Test
    fun cancellingAFetchClearsItsRunningFlagAndAllowsAnotherAttempt() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val core = FakeCore(gate = gate)
            val enrichment = CatalogEnrichmentFetcher(FakeCoreProvider(core), InMemoryTmdbSettings().apply { write("key") }, dispatcher = Dispatchers.Unconfined)
            val run = launch { enrichment.fetch() }
            runCurrent()
            assertTrue(enrichment.state.value.running)
            run.cancelAndJoin()
            assertFalse(enrichment.state.value.running)
            assertNull(enrichment.state.value.error)
            gate.complete(Unit)
            enrichment.fetch()
            assertEquals(2, core.fetchCalls)
        }

    private fun linkedHomeCache() = HomeCacheServer().apply { linked("http://192.168.0.240:7788") }
}

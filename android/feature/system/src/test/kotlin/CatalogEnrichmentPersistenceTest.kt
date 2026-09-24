package system

import data.CatalogEnrichmentFetcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import settings.InMemoryTmdbSettings
import settings.TmdbSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogEnrichmentPersistenceTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun anUnreadableKeyAtInitializationIsReportedWithoutLeakingItsContents() =
        runTest {
            val settings = FailingKeySettings().apply { readFailure = IllegalStateException("secret-key") }
            val vm = FetchViewModel(CatalogEnrichmentFetcher(FakeCoreProvider(FakeCore()), settings))
            assertFalse(vm.state.value.hasKey)
            assertEquals("Could not read the saved TMDB key. Save it again on the TMDB key screen.", vm.state.value.error)
            assertFalse(
                vm.state.value
                    .toString()
                    .contains("secret-key"),
            )
        }

    @Test
    fun aFailedSaveDoesNotClaimTheKeyWasStoredAndCanBeRetried() =
        runTest {
            val settings = FailingKeySettings()
            val vm = FetchViewModel(CatalogEnrichmentFetcher(FakeCoreProvider(FakeCore()), settings))
            settings.writeFailure = IllegalStateException("secret-key")
            vm.saveKey("secret-key")
            assertFalse(vm.state.value.hasKey)
            assertNull(settings.read())
            assertEquals("Could not save the TMDB key. Please try again.", vm.state.value.error)
            assertFalse(
                vm.state.value
                    .toString()
                    .contains("secret-key"),
            )
            settings.writeFailure = null
            vm.saveKey("secret-key")
            assertTrue(vm.state.value.hasKey)
            assertNull(vm.state.value.error)
        }

    @Test
    fun aFailedReplacementPreservesTheKnownStoredKey() =
        runTest {
            val settings = FailingKeySettings().apply { write("old-key") }
            val vm = FetchViewModel(CatalogEnrichmentFetcher(FakeCoreProvider(FakeCore()), settings))
            settings.writeFailure = IllegalStateException("new-key")
            vm.saveKey("new-key")
            assertTrue(vm.state.value.hasKey)
            assertEquals("old-key", settings.read())
            assertEquals("Could not save the TMDB key. Please try again.", vm.state.value.error)
        }

    @Test
    fun anUnreadableKeyDuringFetchStopsBeforeCallingTheCore() =
        runTest {
            val settings = FailingKeySettings().apply { write("key") }
            val core = FakeCore()
            val vm = FetchViewModel(CatalogEnrichmentFetcher(FakeCoreProvider(core), settings))
            settings.readFailure = IllegalStateException("secret-key")
            vm.fetch()
            assertEquals(0, core.fetchCalls)
            assertFalse(vm.state.value.running)
            assertEquals("Could not read the saved TMDB key. Save it again on the TMDB key screen.", vm.state.value.error)
            assertFalse(
                vm.state.value
                    .toString()
                    .contains("secret-key"),
            )
        }

    @Test
    fun cancellationOfAKeyReadPropagatesWithoutDisplayingAFailure() =
        runTest {
            val settings = FailingKeySettings().apply { readGate = CompletableDeferred() }
            val enrichment = CatalogEnrichmentFetcher(FakeCoreProvider(FakeCore()), settings)
            val read = launch { enrichment.refreshKeyStatus() }
            runCurrent()
            read.cancelAndJoin()
            assertTrue(read.isCancelled)
            assertNull(enrichment.state.value.error)
            assertFalse(enrichment.state.value.hasKey)
        }

    @Test
    fun cancellationOfAKeyWritePropagatesWithoutDisplayingAFailure() =
        runTest {
            val settings = FailingKeySettings().apply { writeFailure = CancellationException("cancelled") }
            val enrichment = CatalogEnrichmentFetcher(FakeCoreProvider(FakeCore()), settings)
            val save = launch { enrichment.saveKey("key") }
            save.join()
            assertTrue(save.isCancelled)
            assertNull(enrichment.state.value.error)
            assertFalse(enrichment.state.value.hasKey)
        }
}

private class FailingKeySettings : TmdbSettings {
    private val stored = InMemoryTmdbSettings()
    var readFailure: Exception? = null
    var writeFailure: Exception? = null
    var readGate: CompletableDeferred<Unit>? = null

    override suspend fun read(): String? {
        readGate?.await()
        readFailure?.let { throw it }
        return stored.read()
    }

    override suspend fun write(key: String) {
        writeFailure?.let { throw it }
        stored.write(key)
    }

    override suspend fun clear() = stored.clear()
}

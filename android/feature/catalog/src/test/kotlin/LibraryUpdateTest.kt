package catalog

import data.BackdropWidth
import data.CatalogEnrichmentFetcher
import data.CatalogRepository
import data.LibraryUpdate
import data.LibraryUpdateKind
import data.RefreshOutcome
import data.settings.InMemoryTmdbSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import model.MediaSet
import testing.FakeCore
import testing.FakeCoreProvider
import uniffi.mediagram_core.CoreInterface
import uniffi.mediagram_core.FetchReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LibraryUpdateTest {
    @Test
    fun anUpdateRefreshesFetchesAndRereadsWithoutAnyoneWatching() =
        runTest {
            val fixture = UpdateFixture()
            fixture.run()
            assertEquals(listOf("read", "fetch", "read"), fixture.steps)
            assertEquals(1, fixture.repository.refreshes)
            assertTrue(fixture.artworkVisible)
            assertFalse(fixture.update.state.value.reading)
            assertFalse(fixture.update.state.value.fetching)
        }

    @Test
    fun fetchingWaitsForTheRefreshAndArtworkIsReadAfterItIsWritten() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val fixture = UpdateFixture().apply { repository.refreshGate = gate }
            val update = launch { fixture.run() }
            runCurrent()
            assertTrue(fixture.update.state.value.reading)
            assertTrue(fixture.steps.isEmpty())
            gate.complete(Unit)
            update.join()
            assertEquals(listOf("read", "fetch", "read"), fixture.steps)
            assertTrue(fixture.artworkVisible)
        }

    @Test
    fun aManualRefreshFailureStillFillsGapsInTheHeldCatalogAndSaysWhy() =
        runTest {
            val fixture = UpdateFixture(FakeCatalogRepository(movies = 1, refreshFails = true))
            fixture.run()
            assertEquals(listOf("read", "fetch", "read"), fixture.steps)
            assertEquals(fixture.report, fixture.update.state.value.report)
            assertEquals("Could not refresh the library", fixture.update.library.value?.refusal)
        }

    @Test
    fun aFailedPublishedReadDoesNotFetch() =
        runTest {
            val fixture = UpdateFixture(FakeCatalogRepository(movies = 1, refreshFails = true))
            fixture.run(LibraryUpdateKind.Published)
            assertEquals(listOf("read"), fixture.steps)
            assertEquals("Could not refresh the library", fixture.update.library.value?.refusal)
            assertNull(fixture.update.state.value.report)
        }

    @Test
    fun aPublishedReadFetchesAndRereadsQuietly() =
        runTest {
            val fixture = UpdateFixture()
            fixture.run(LibraryUpdateKind.Published)
            assertEquals(listOf("read", "fetch", "read"), fixture.steps)
            assertTrue(fixture.artworkVisible)
            assertNull(fixture.update.state.value.report)
        }

    @Test
    fun aBackdropUpgradeAloneRereadsArtwork() =
        runTest {
            val fixture = UpdateFixture(report = FetchReport(0u, 0u, 1u, 0u, 0u, 0u, 0u, 0u))
            fixture.run()
            assertEquals(listOf("read", "fetch", "read"), fixture.steps)
        }

    @Test
    fun aRunThatFetchedNoArtworkDoesNotRereadIt() =
        runTest {
            val fixture = UpdateFixture(report = FetchReport(0u, 1u, 0u, 1u, 0u, 1u, 0u, 0u))
            fixture.run()
            assertEquals(listOf("read", "fetch"), fixture.steps)
        }

    /** The library is already read once by then, so a Read is the refresh and the read after it alone. */
    @Test
    fun aReadWithoutAnUpdateDoesNotSpendArtwork() =
        runTest {
            val fixture = UpdateFixture()
            fixture.run(LibraryUpdateKind.Read)
            fixture.steps.clear()
            fixture.run(LibraryUpdateKind.Read)
            assertEquals(listOf("read"), fixture.steps)
        }

    @Test
    fun aReadThatInstalledANewerIndexFetchesItsArtworkQuietly() =
        runTest {
            val fixture = UpdateFixture().apply { repository.refreshOutcome = RefreshOutcome.Updated }
            fixture.run(LibraryUpdateKind.Read)
            assertEquals(listOf("read", "read", "fetch", "read"), fixture.steps)
            assertTrue(fixture.artworkVisible)
            assertNull(fixture.update.state.value.report)
        }

    @Test
    fun aRefusedReadDoesNotFetch() =
        runTest {
            val fixture = UpdateFixture(FakeCatalogRepository(movies = 1, refreshFails = true))
            fixture.run(LibraryUpdateKind.Read)
            assertEquals(listOf("read", "read"), fixture.steps)
        }

    /** A refresh can be a download of the whole index, so a first start shows what is already installed meanwhile. */
    @Test
    fun aFirstReadShowsWhatIsInstalledBeforeTheRefreshReturns() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val fixture = UpdateFixture().apply { repository.refreshGate = gate }
            val update = launch { fixture.run(LibraryUpdateKind.Read) }
            runCurrent()
            val installed = fixture.update.library.value
            assertEquals(1, installed?.sets?.getOrNull()?.size)
            assertNull(installed?.refusal)
            assertTrue(fixture.update.state.value.reading)
            gate.complete(Unit)
            update.join()
            assertEquals(listOf("read", "read"), fixture.steps)
            assertFalse(installed === fixture.update.library.value, "the read after the refresh is published too, even of the same sets")
        }

    @Test
    fun nothingInstalledIsNotShownAsALibrary() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val fixture = UpdateFixture(FakeCatalogRepository(onDisk = false)).apply { repository.refreshGate = gate }
            val update = launch { fixture.run(LibraryUpdateKind.Read) }
            runCurrent()
            assertNull(fixture.update.library.value)
            gate.complete(Unit)
            update.join()
        }

    @Test
    fun aLocalReadFailureIsPublishedForTheViewToSay() =
        runTest {
            val failure = IllegalStateException("catalog unreadable")
            val fixture = UpdateFixture(FakeCatalogRepository(movies = 1).apply { readFailure = failure })
            fixture.run(LibraryUpdateKind.Read)
            assertSame(failure, fixture.update.library.value?.sets?.exceptionOrNull())
        }

    @Test
    fun cancellationDuringRefreshDoesNotFetchAndReleasesTheNextUpdate() =
        runTest {
            val fixture = UpdateFixture().apply { repository.refreshGate = CompletableDeferred() }
            val cancelled = launch { fixture.run() }
            runCurrent()
            cancelled.cancelAndJoin()
            assertFalse(fixture.update.state.value.reading)
            assertTrue(fixture.steps.isEmpty())
            fixture.repository.refreshGate = null
            fixture.run()
            assertEquals(listOf("read", "fetch", "read"), fixture.steps)
        }

    @Test
    fun cancellationDuringFetchDoesNotRereadAndClearsTheFetchingState() =
        runTest {
            val fixture = UpdateFixture().apply { fetchGate = CompletableDeferred() }
            val cancelled = launch { fixture.run() }
            runCurrent()
            assertTrue(fixture.update.state.value.fetching)
            cancelled.cancelAndJoin()
            assertEquals(listOf("read", "fetch"), fixture.steps)
            assertFalse(fixture.update.state.value.fetching)
            assertNull(fixture.update.state.value.error)
            fixture.fetchGate = null
            fixture.run()
            assertTrue(fixture.artworkVisible)
        }

    @Test
    fun aSecondUpdateCannotReadTheLibraryDuringTheFirstFetch() =
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
            assertEquals(listOf("read", "fetch", "read", "read", "fetch", "read"), fixture.steps)
        }

    @Test
    fun dismissingClearsTheLastResult() =
        runTest {
            val fixture = UpdateFixture()
            fixture.run()
            assertEquals(fixture.report, fixture.update.state.value.report)
            fixture.update.dismissResult()
            assertNull(fixture.update.state.value.report)
        }
}

private class UpdateFixture(
    val repository: FakeCatalogRepository = FakeCatalogRepository(movies = 1),
    val report: FetchReport = FetchReport(1u, 0u, 0u, 0u, 1u, 0u, 0u, 0u),
) {
    val steps = mutableListOf<String>()
    var fetchGate: CompletableDeferred<Unit>? = null
    private val core =
        object : CoreInterface by FakeCore() {
            override suspend fun fetchMissing(
                tmdbKey: String,
                language: String,
                backdropWidth: UInt,
            ): FetchReport {
                steps += "fetch"
                fetchGate?.await()
                repository.postersArrived = true
                return report
            }
        }

    /** Each finished read of the installed sets is a step, whichever part of an update asked for it. */
    private val recorded =
        object : CatalogRepository by repository {
            override suspend fun sets(): List<MediaSet> = repository.sets().also { steps += "read" }
        }
    // On the test's own thread, so a step the core takes has been taken when a test looks.
    private val enrichment = CatalogEnrichmentFetcher(FakeCoreProvider(core), InMemoryTmdbSettings(), BackdropWidth { 780 }, Dispatchers.Unconfined)
    val update = LibraryUpdate(recorded, enrichment)

    val artworkVisible: Boolean
        get() = update.library.value?.sets?.getOrNull()?.all { it.posterPath != null } == true

    suspend fun run(kind: LibraryUpdateKind = LibraryUpdateKind.Manual) {
        enrichment.saveKey("test-key")
        update.update(kind)
    }
}

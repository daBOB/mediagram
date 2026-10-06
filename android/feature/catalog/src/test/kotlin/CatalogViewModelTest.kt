package catalog

import app.cash.turbine.test
import data.CatalogEnrichmentFetcher
import data.CatalogRepository
import data.LibraryEvents
import data.LibraryUpdateCoordinator
import data.WatchStateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import model.Kind
import model.ListOfSets
import model.Profile
import model.ProfileRequest
import model.WatchSnapshot
import org.junit.After
import playback.ActivePreload
import playback.FilmPreloadState
import playback.FilmPreloading
import settings.InMemoryTmdbSettings
import testing.CatalogCoreProvider
import testing.FakeCore
import testing.WatchStateFixture
import uniffi.mediagram_core.CoreInterface
import uniffi.mediagram_core.FetchReport
import uniffi.mediagram_core.LibraryEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun catalogViewModel(
    repository: CatalogRepository,
    watchState: WatchStateRepository,
    enrichment: CatalogEnrichmentFetcher = CatalogEnrichmentFetcher(CatalogCoreProvider(FakeCore()), InMemoryTmdbSettings()),
    filmPreloader: FilmPreloading = FilmPreloading.Noop,
    // Last so existing trailing-lambda call sites (`catalogViewModel(a, b) { pushed }`,
    // LibraryEvents being a fun interface) keep binding to this one.
    events: LibraryEvents = LibraryEvents.None,
): CatalogViewModel =
    CatalogViewModel(
        repository,
        watchState,
        LibraryUpdateCoordinator(repository, enrichment),
        events,
        filmPreloader = filmPreloader,
    )

/** A [FilmPreloading] a test can fire [heldEvents]/[unheldEvents] through directly — nothing here ever actually preloads anything. */
private class FakeFilmPreloading : FilmPreloading {
    private val _heldEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val heldEvents: SharedFlow<String> = _heldEvents

    private val _unheldEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val unheldEvents: SharedFlow<String> = _unheldEvents

    override val hasWork: StateFlow<Boolean> = MutableStateFlow(false)
    override val active: StateFlow<ActivePreload?> = MutableStateFlow(null)

    override fun stateOf(setId: String, totalBytes: Long): Flow<FilmPreloadState> = MutableStateFlow(FilmPreloadState.Idle(0L, totalBytes))
    override fun enqueue(setId: String, title: String, totalBytes: Long) = Unit
    override fun cancel(setId: String) = Unit
    override fun remove(setId: String) = Unit
    override fun pauseForTimeLimit() = Unit

    fun emitHeld(setId: String) = _heldEvents.tryEmit(setId)
    fun emitUnheld(setId: String) = _unheldEvents.tryEmit(setId)
}

private val ANA = Profile("a", "Ana")
private val MIA = Profile("k", "Mia", kids = true)
private const val WATCHLIST_NOTICE = "Could not confirm the Watchlist update. Check it and try again."
private const val EDITORS_CHOICE_NOTICE = "Could not confirm the editor's choice update. Check it and try again."

/**
 * Uses a standard (queued, not eager) test dispatcher tied to the same
 * scheduler as [runTest], not the unconfined one most ViewModel tests
 * reach for: the refresh this ViewModel launches at construction must
 * still be queued, not already run, by the time the state flow gets its
 * first subscriber, or the intermediate [CatalogUiState.Loading] value is
 * never observed.
 */
class CatalogViewModelTest {
    @Test
    fun aLocalReadFailureKeepsPriorShelvesAndSaysWhatFailed() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository = FakeCatalogRepository(movies = 1)
            val vm = catalogViewModel(repository, WatchStateFixture().repository)
            vm.state.test {
                awaitItem()
                val before = awaitItem() as CatalogUiState.Ready
                repository.readFailure = IllegalStateException("catalog unreadable")
                val gate = CompletableDeferred<Unit>()
                repository.refreshGate = gate
                vm.reload()
                assertTrue((awaitItem() as CatalogUiState.Ready).refreshing)
                gate.complete(Unit)
                val after = awaitItem()
                assertTrue(after is CatalogUiState.Ready)
                assertEquals(before.shelves, after.shelves)
                assertEquals("Could not read the library. Try again.", after.notice)
                assertFalse(after.refreshing)
            }
        }

    @Test
    fun aFirstLocalReadFailureIsNotAnEmptyLibrary() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository = FakeCatalogRepository().apply { readFailure = IllegalStateException("catalog unreadable") }
            val vm = catalogViewModel(repository, WatchStateFixture().repository)
            vm.state.test {
                awaitItem()
                assertEquals(CatalogUiState.Failed("Could not read the library. Try again."), awaitItem())
            }
        }

    @Test
    fun artworkRegroupingDoesNotFinishAnActiveRefresh() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository = FakeCatalogRepository(movies = 1)
            val vm = catalogViewModel(repository, WatchStateFixture().repository)
            vm.state.test {
                awaitItem()
                awaitItem()
                val gate = CompletableDeferred<Unit>()
                repository.refreshGate = gate
                vm.reload()
                assertTrue((awaitItem() as CatalogUiState.Ready).refreshing)
                repository.postersArrived = true
                vm.showFetched()
                assertTrue((awaitItem() as CatalogUiState.Ready).refreshing)
                gate.complete(Unit)
                assertFalse((awaitItem() as CatalogUiState.Ready).refreshing)
            }
        }

    @Test
    fun aManualUpdateCompletesWithoutCollectingTransientUiStates() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository = FakeCatalogRepository(movies = 1)
            var fetches = 0
            val core =
                object : CoreInterface by FakeCore() {
                    override suspend fun fetchMissing(
                        tmdbKey: String,
                        language: String,
                        backdropWidth: UInt,
                    ): FetchReport {
                        fetches += 1
                        repository.postersArrived = true
                        return FetchReport(1u, 0u, 0u, 0u, 0u, 0u, 0u, 0u)
                    }
                }
            val enrichment = CatalogEnrichmentFetcher(CatalogCoreProvider(core), InMemoryTmdbSettings().apply { write("key") })
            val vm = catalogViewModel(repository, WatchStateFixture().repository, enrichment)
            vm.update()
            runCurrent()
            assertEquals(1, repository.refreshes)
            assertEquals(1, fetches)
            assertEquals(2, repository.reads, "the awaited artwork step rereads local rows before any UI observes them")
            vm.state.test {
                awaitItem()
                val ready = awaitItem() as CatalogUiState.Ready
                assertEquals(
                    "/artwork/movie-0.jpg",
                    (
                        ready.shelves
                            .single { it.title == "Movies" }
                            .entries
                            .single() as Entry.Film
                    ).set.posterPath,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun leavingTheCompositionDoesNotCancelAManualUpdate() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository = FakeCatalogRepository(movies = 1)
            var fetches = 0
            val core =
                object : CoreInterface by FakeCore() {
                    override suspend fun fetchMissing(
                        tmdbKey: String,
                        language: String,
                        backdropWidth: UInt,
                    ): FetchReport {
                        fetches += 1
                        return FetchReport(0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u)
                    }
                }
            val enrichment = CatalogEnrichmentFetcher(CatalogCoreProvider(core), InMemoryTmdbSettings().apply { write("key") })
            val vm = catalogViewModel(repository, WatchStateFixture().repository, enrichment)
            val gate = CompletableDeferred<Unit>()
            vm.state.test {
                awaitItem()
                awaitItem()
                repository.refreshGate = gate
                vm.update()
                assertTrue((awaitItem() as CatalogUiState.Ready).refreshing)
                cancelAndIgnoreRemainingEvents()
            }
            advanceTimeBy(6_000)
            runCurrent()
            assertEquals(0, fetches)
            gate.complete(Unit)
            runCurrent()
            assertEquals(1, fetches)
            assertFalse(enrichment.state.value.running)
        }

    @Test
    fun failedCollectionWritesKeepTheShelvesAndReportAnActionableNotice() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val watch = WatchStateFixture()
            watch.provider.beforeCore = { throw IllegalStateException("private-storage-detail") }
            val vm = catalogViewModel(FakeCatalogRepository(movies = 1), watch.repository)
            val actions: List<Pair<String, () -> Unit>> =
                listOf(
                    "create" to { vm.createList("Favourite") },
                    "rename" to { vm.renameList("l1", "Renamed") },
                    "delete" to { vm.deleteList("l1") },
                    "update" to { vm.setInList("l1", "movie-0", true) },
                )
            vm.state.test {
                awaitItem()
                val before = awaitItem() as CatalogUiState.Ready
                for ((verb, action) in actions) {
                    action()
                    val after = awaitItem() as CatalogUiState.Ready
                    assertEquals(before.shelves, after.shelves)
                    assertEquals("Could not $verb the collection. Please try again.", after.notice)
                    assertFalse(after.notice!!.contains("private-storage-detail"))
                    assertEquals(before.watch, after.watch)
                }
            }
        }

    @Test
    fun refusedCollectionWritesAreNotReportedAsSuccess() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            // With nobody chosen the repository refuses every list write.
            val vm = catalogViewModel(FakeCatalogRepository(movies = 1), WatchStateFixture(chosen = null).repository)
            vm.state.test {
                awaitItem()
                val before = awaitItem() as CatalogUiState.Ready
                vm.createList("Favourite")
                val after = awaitItem() as CatalogUiState.Ready
                assertEquals("Could not create the collection. Please try again.", after.notice)
                assertEquals(before.shelves, after.shelves)
                assertTrue(after.watch.collections.isEmpty())
            }
        }

    @Test
    fun retryingACollectionWriteClearsOnlyItsOwnFailureNotice() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val watch = WatchStateFixture()
            watch.provider.beforeCore = { throw IllegalStateException("cannot write") }
            val vm = catalogViewModel(FakeCatalogRepository(movies = 1), watch.repository)
            vm.state.test {
                awaitItem()
                awaitItem()
                vm.createList("Favourite")
                assertEquals("Could not create the collection. Please try again.", (awaitItem() as CatalogUiState.Ready).notice)
                watch.provider.beforeCore = {}
                vm.createList("Favourite")
                runCurrent()
                val after = vm.state.value as CatalogUiState.Ready
                assertEquals(null, after.notice)
                assertEquals(listOf("Favourite"), after.watch.collections.map { it.name })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aSuccessfulCollectionWritePreservesAnUnrelatedRefreshNotice() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val vm = catalogViewModel(FakeCatalogRepository(movies = 1, refreshFails = true), WatchStateFixture().repository)
            vm.state.test {
                awaitItem()
                val before = awaitItem() as CatalogUiState.Ready
                vm.createList("Favourite")
                val after = awaitItem() as CatalogUiState.Ready
                assertEquals(before.notice, after.notice)
                assertEquals("Could not refresh the library", after.notice)
                assertEquals(listOf("Favourite"), after.watch.collections.map { it.name })
            }
        }

    @Test
    fun aCancelledCollectionWriteDoesNotBecomeAnErrorNotice() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val watch = WatchStateFixture()
            watch.provider.beforeCore = { throw CancellationException("cancelled") }
            val vm = catalogViewModel(FakeCatalogRepository(movies = 1), watch.repository)
            vm.state.test {
                awaitItem()
                val before = awaitItem() as CatalogUiState.Ready
                vm.createList("Favourite")
                runCurrent()
                expectNoEvents()
                assertEquals(before, vm.state.value)
                watch.provider.beforeCore = {}
                vm.createList("Favourite")
                assertEquals(listOf("Favourite"), (awaitItem() as CatalogUiState.Ready).watch.collections.map { it.name })
            }
        }

    /**
     * A My List or editor's-choice write the core throws on — a core closed
     * by a sign-out racing the tap — leaves the app up with a notice on the
     * shelves, and the same write landing later takes that notice back off.
     */
    private suspend fun TestScope.aFailedMarkSaysSoUntilItLands(
        notice: String,
        write: CatalogViewModel.() -> Unit,
        landed: (WatchSnapshot) -> Boolean,
    ) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val watch = WatchStateFixture()
        watch.provider.beforeCore = { throw IllegalStateException("core closed") }
        val vm = catalogViewModel(FakeCatalogRepository(movies = 1), watch.repository)
        vm.state.test {
            awaitItem()
            val before = awaitItem() as CatalogUiState.Ready
            vm.write()
            val failed = awaitItem() as CatalogUiState.Ready
            assertEquals(notice, failed.notice)
            assertEquals(before.shelves, failed.shelves)
            watch.provider.beforeCore = {}
            vm.write()
            runCurrent()
            val after = vm.state.value as CatalogUiState.Ready
            assertNull(after.notice)
            assertTrue(landed(after.watch))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedMyListWriteSaysSoUntilItLands() =
        runTest {
            aFailedMarkSaysSoUntilItLands(WATCHLIST_NOTICE, { setWatchlisted("movie-0", true) }) { "movie-0" in it.watchlist }
        }

    @Test
    fun aFailedMyListToggleSaysSoUntilItLands() =
        runTest {
            aFailedMarkSaysSoUntilItLands(WATCHLIST_NOTICE, { toggleWatchlist("movie-0") }) { "movie-0" in it.watchlist }
        }

    @Test
    fun aFailedEditorsChoiceToggleSaysSoUntilItLands() =
        runTest {
            aFailedMarkSaysSoUntilItLands(EDITORS_CHOICE_NOTICE, { toggleEditorsChoice("movie-0") }) { it.editorsChoice == "movie-0" }
        }

    /** A title page's buttons name only the title: whether it is on or off is the snapshot's to say. */
    @Test
    fun titlePageTogglesTurnAMarkOnAndBackOff() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val watch = WatchStateFixture()
            val vm = catalogViewModel(FakeCatalogRepository(movies = 1), watch.repository)

            vm.toggleWatchlist("movie-0")
            advanceUntilIdle()
            assertEquals(listOf("movie-0"), watch.repository.snapshot.value.watchlist)
            vm.toggleWatchlist("movie-0")
            advanceUntilIdle()
            assertEquals(emptyList(), watch.repository.snapshot.value.watchlist)

            vm.toggleEditorsChoice("movie-0")
            advanceUntilIdle()
            assertEquals("movie-0", watch.repository.snapshot.value.editorsChoice)
            vm.toggleEditorsChoice("movie-0")
            advanceUntilIdle()
            assertNull(watch.repository.snapshot.value.editorsChoice)
        }

    @Test
    fun aKidsProfileCannotPinTheEditorsChoice() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val watch = WatchStateFixture(listOf(MIA))
            val vm = catalogViewModel(FakeCatalogRepository(movies = 1), watch.repository)

            vm.toggleEditorsChoice("movie-0")
            advanceUntilIdle()

            assertNull(watch.core.editorsChoice())
        }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun shelvesAreGroupedByKind() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val vm = catalogViewModel(FakeCatalogRepository(movies = 2, episodes = 1, tutorials = 0), WatchStateFixture().repository)
            vm.state.test {
                assertEquals(CatalogUiState.Loading, awaitItem())
                val ready = awaitItem() as CatalogUiState.Ready
                assertEquals(listOf("Movies", "Series", "Documentaries"), ready.shelves.map { it.title })
            }
        }

    /**
     * A catalog is a file on this device and stays a whole library when the
     * channel cannot be reached. Losing it over a failed round trip is the
     * one failure a viewer has no way to work around — and the channel is
     * reachable far less reliably than the file is.
     */
    @Test
    fun aFailedRefreshKeepsTheLibraryAlreadyOnThisDevice() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val vm = catalogViewModel(FakeCatalogRepository(movies = 2, refreshFails = true), WatchStateFixture().repository)
            vm.state.test {
                awaitItem()
                val ready = awaitItem() as CatalogUiState.Ready
                assertEquals(listOf("Movies", "Documentaries"), ready.shelves.map { it.title })
            }
        }

    /** Kept, but not quietly: a library that stopped updating has to say so. */
    @Test
    fun aRefreshThatFailedIsSaidRatherThanSwallowed() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val vm = catalogViewModel(FakeCatalogRepository(movies = 1, refreshFails = true), WatchStateFixture().repository)
            vm.state.test {
                awaitItem()
                assertEquals("Could not refresh the library", (awaitItem() as CatalogUiState.Ready).notice)
            }
        }

    /**
     * The whole point of the menu action. The state flow used to be a cold
     * flow handed straight to `stateIn`, so it ran once per subscription
     * and never again — asking for the library a second time was something
     * only a force-stop could do.
     */
    @Test
    fun askingAgainReadsTheChannelAgain() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository = FakeCatalogRepository(movies = 2)
            val vm = catalogViewModel(repository, WatchStateFixture().repository)
            vm.state.test {
                awaitItem()
                awaitItem() as CatalogUiState.Ready
                assertEquals(1, repository.refreshes)

                vm.reload()
                advanceUntilIdle()

                assertEquals(2, repository.refreshes)
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * A reload is said over the shelves rather than instead of them. Only
     * the first load has nothing to show, and taking a whole library away
     * for as long as a network round trip takes would be a worse answer to
     * "refresh this" than the wait it was reporting.
     */
    @Test
    fun askingAgainKeepsTheShelvesItIsAboutToReplace() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository = FakeCatalogRepository(movies = 2)
            val vm = catalogViewModel(repository, WatchStateFixture().repository)
            vm.state.test {
                assertEquals(CatalogUiState.Loading, awaitItem())
                val before = awaitItem() as CatalogUiState.Ready
                assertFalse(before.refreshing)
                val gate = CompletableDeferred<Unit>()
                repository.refreshGate = gate
                vm.reload()
                val during = awaitItem() as CatalogUiState.Ready
                assertTrue(during.refreshing)
                assertEquals(before.shelves, during.shelves)
                gate.complete(Unit)
                assertFalse((awaitItem() as CatalogUiState.Ready).refreshing)
            }
        }

    @Test
    fun aFailedRefreshWithNothingOnDiskSurfacesAsFailed() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val vm = catalogViewModel(FakeCatalogRepository(refreshFails = true, onDisk = false), WatchStateFixture().repository)
            vm.state.test {
                awaitItem()
                assertTrue(awaitItem() is CatalogUiState.Failed)
            }
        }

    /**
     * Another device publishing an index is a reason to read the channel
     * again, exactly as pressing Update is. Another device's watch state is
     * not the catalog's business, and must not cost an index download.
     */
    @Test
    fun aNewIndexFromAnotherDeviceReadsTheChannelAgain() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val pushed = MutableSharedFlow<LibraryEvent>()
            val repository = FakeCatalogRepository(movies = 2)
            val vm = catalogViewModel(repository, WatchStateFixture().repository) { pushed }
            vm.state.test {
                assertEquals(CatalogUiState.Loading, awaitItem())
                awaitItem() as CatalogUiState.Ready
                assertEquals(1, repository.refreshes)

                pushed.emit(LibraryEvent.STATE)
                runCurrent()
                assertEquals(1, repository.refreshes, "watch state is not a catalog change")

                val gate = CompletableDeferred<Unit>()
                repository.refreshGate = gate
                pushed.emit(LibraryEvent.INDEX)
                assertTrue((awaitItem() as CatalogUiState.Ready).refreshing)
                gate.complete(Unit)
                assertFalse((awaitItem() as CatalogUiState.Ready).refreshing)
                assertEquals(2, repository.refreshes)
            }
        }

    @Test
    fun onlyAPushedReadThatSucceededFetchesQuietly() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            for (fails in listOf(false, true)) {
                val pushed = MutableSharedFlow<LibraryEvent>()
                var fetches = 0
                val core =
                    object : CoreInterface by FakeCore() {
                        override suspend fun fetchMissing(
                            tmdbKey: String,
                            language: String,
                            backdropWidth: UInt,
                        ): FetchReport {
                            fetches += 1
                            return FetchReport(0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u)
                        }
                    }
                val enrichment = CatalogEnrichmentFetcher(CatalogCoreProvider(core), InMemoryTmdbSettings().apply { write("key") })
                val vm =
                    catalogViewModel(
                        FakeCatalogRepository(movies = 1, refreshFails = fails),
                        WatchStateFixture().repository,
                        enrichment,
                    ) { pushed }
                vm.state.test {
                    awaitItem()
                    awaitItem()
                    vm.reload()
                    advanceUntilIdle()
                    assertEquals(0, fetches)
                    pushed.emit(LibraryEvent.INDEX)
                    runCurrent()
                    assertEquals(if (fails) 0 else 1, fetches)
                    assertEquals(null, enrichment.state.value.report)
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

    /**
     * The defect this closes: a card looks its poster up when the shelves are
     * built, and a fetch finishes after they are, so fetched artwork sat on
     * disk behind initials until the next reload. Showing it must not ask the
     * channel again, and must not flag the library as refreshing.
     */
    @Test
    fun artworkAFetchLaidDownIsShownWithoutAskingTheChannel() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository = FakeCatalogRepository(movies = 1)
            val vm = catalogViewModel(repository, WatchStateFixture().repository)
            vm.state.test {
                awaitItem()
                val before = awaitItem() as CatalogUiState.Ready
                assertEquals(listOf(null), before.shelves.flatMap { it.entries }.map { (it as Entry.Film).set.posterPath })

                repository.postersArrived = true
                vm.showFetched()

                val after = awaitItem() as CatalogUiState.Ready
                assertFalse(after.refreshing)
                assertEquals(listOf("/artwork/movie-0.jpg"), after.shelves.flatMap { it.entries }.map { (it as Entry.Film).set.posterPath })
                assertEquals(1, repository.refreshes, "showing artwork is not a read of the channel")
            }
        }

    /**
     * Neither a sync round nor a write from the player is a catalog change,
     * so [WatchStateRepository.snapshot] is joined in rather than re-read —
     * a later value on that flow alone must still reach [CatalogUiState.Ready.watch].
     */
    @Test
    fun aChangedSnapshotReachesReadyWithoutARereadOfTheChannel() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository = FakeCatalogRepository(movies = 1)
            val watchState = WatchStateFixture()
            val vm = catalogViewModel(repository, watchState.repository)
            vm.state.test {
                awaitItem()
                val before = awaitItem() as CatalogUiState.Ready
                assertEquals(WatchSnapshot.Empty, before.watch)

                watchState.repository.setProgress("movie-0", 30.0, 3_600.0)

                val after = awaitItem() as CatalogUiState.Ready
                assertEquals(listOf("movie-0" to 30.0), after.watch.progress.map { it.setId to it.at })
                assertEquals(1, repository.refreshes, "a changed snapshot is not a reason to read the channel again")
            }
        }

    /** The Collections tab's "New list", and its rename, delete and membership writes — each a fire-and-forget wrapper over the repository. */
    @Test
    fun createListReachesTheRepositoryAndTheNextSnapshot() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val watchState = WatchStateFixture()
            val vm = catalogViewModel(FakeCatalogRepository(movies = 1), watchState.repository)
            vm.state.test {
                awaitItem()
                awaitItem()

                vm.createList("Favourites")
                val after = awaitItem() as CatalogUiState.Ready

                assertEquals(listOf("Favourites"), after.watch.collections.map(ListOfSets::name))
                assertEquals(listOf("Favourites"), watchState.core.snapshot(WatchStateFixture.VIEWER.id).collections.map { it.name })
            }
        }

    @Test
    fun renameAndDeleteListReachTheRepository() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val watchState = WatchStateFixture(seed = { createList("Old name") })
            val list = watchState.repository.snapshot.value.collections.single().id
            val vm = catalogViewModel(FakeCatalogRepository(movies = 1), watchState.repository)
            vm.state.test {
                awaitItem()
                awaitItem()

                vm.renameList(list, "New name")
                assertEquals(listOf("New name"), (awaitItem() as CatalogUiState.Ready).watch.collections.map(ListOfSets::name))

                vm.deleteList(list)
                assertTrue((awaitItem() as CatalogUiState.Ready).watch.collections.isEmpty())
            }
        }

    @Test
    fun setInListFilesAndRemovesATitle() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val watchState = WatchStateFixture(seed = { createList("Favourites") })
            val list = watchState.repository.snapshot.value.collections.single().id
            val vm = catalogViewModel(FakeCatalogRepository(movies = 1), watchState.repository)
            vm.state.test {
                awaitItem()
                awaitItem()

                vm.setInList(list, "movie-0", true)
                assertEquals(
                    listOf("movie-0"),
                    (awaitItem() as CatalogUiState.Ready)
                        .watch.collections
                        .single()
                        .items,
                )

                vm.setInList(list, "movie-0", false)
                assertTrue(
                    (awaitItem() as CatalogUiState.Ready)
                        .watch.collections
                        .single()
                        .items
                        .isEmpty(),
                )
            }
        }

    @Test
    fun aKidsProfileSeesOnlyItsTitlesAndSwitchingBackRestoresTheRest() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository =
                FakeCatalogRepository(
                    given =
                        listOf(
                            fakeSet(Kind.MOVIE, "Family").copy(fsk = "6"),
                            fakeSet(Kind.MOVIE, "Grown").copy(fsk = "16"),
                            fakeSet(Kind.MOVIE, "Marked"),
                        ),
                )
            val watch = WatchStateFixture(listOf(MIA, ANA), seed = { setKids("Marked", 12) })
            val vm = catalogViewModel(repository, watch.repository)
            vm.state.test {
                awaitItem()
                val kidsView = awaitItem() as CatalogUiState.Ready
                val ids = kidsView.shelves.flatMap { it.entries }.filterIsInstance<Entry.Film>().map { it.set.setId }
                assertEquals(setOf("Family", "Marked"), ids.toSet())
                val readsBefore = repository.reads

                watch.repository.chooseProfile(ANA.id)
                val adultView = awaitItem() as CatalogUiState.Ready
                val all = adultView.shelves.flatMap { it.entries }.filterIsInstance<Entry.Film>().map { it.set.setId }
                assertEquals(setOf("Family", "Grown", "Marked"), all.toSet())
                assertEquals(readsBefore, repository.reads)
            }
        }

    /**
     * A `docu` set is never looked up at a provider, so it never carries an
     * FSK rating and stays [model.KidsVerdict.UNRATED] — hidden from a kids
     * profile unless hand-marked, the same rule any other unrated title
     * follows. The Documentaries shelf is not omitted for being empty the
     * way Movies/Series/Tutorials are, so a kids profile with nothing
     * marked reaches a present, empty Documentaries shelf rather than
     * never seeing one at all — the scenario the empty state on that page
     * has to draw something for.
     */
    @Test
    fun aKidsProfileWithAnUnratedDocumentaryDoesNotSeeItButStillGetsAPresentEmptyShelf() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository = FakeCatalogRepository(
                given = listOf(fakeSet(Kind.MOVIE, "Family").copy(fsk = "6"), fakeSet(Kind.DOCUMENTARY, "Baraka")),
            )
            val vm = catalogViewModel(repository, WatchStateFixture(listOf(MIA)).repository)
            vm.state.test {
                awaitItem()
                val kidsView = awaitItem() as CatalogUiState.Ready
                val documentaries = kidsView.shelves.single { it.title == DOCUMENTARIES }
                assertEquals(emptyList(), documentaries.entries)
            }
        }

    /**
     * The kids filter runs on [MediaSet]s, before `shelvesOf` ever splits
     * anime out — the same filter every shelf gets, not a rule Anime needed
     * of its own. A kids profile that cannot see the one anime title in the
     * library gets no Anime tab at all, the same way it gets no Movies tab
     * with nothing rated for it.
     */
    @Test
    fun aKidsProfileWithNoVisibleAnimeGetsNoAnimeTab() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository = FakeCatalogRepository(
                given = listOf(
                    fakeSet(Kind.MOVIE, "Family").copy(fsk = "6"),
                    fakeSet(Kind.MOVIE, "Grown Up Anime").copy(fsk = "16", anime = true),
                ),
            )
            val vm = catalogViewModel(repository, WatchStateFixture(listOf(MIA)).repository)
            vm.state.test {
                awaitItem()
                val kidsView = awaitItem() as CatalogUiState.Ready
                assertNull(kidsView.shelves.find { it.title == ANIME }, "Anime is omitted at zero, the same as Movies/Series/Tutorials")
            }
        }

    /** The mirror case: a rated anime title still reaches a kids profile, on its own Anime tab. */
    @Test
    fun aKidsProfileWithARatedAnimeTitleGetsTheAnimeTab() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository = FakeCatalogRepository(
                given = listOf(
                    fakeSet(Kind.MOVIE, "Grown Up Anime").copy(fsk = "16", anime = true),
                    fakeSet(Kind.MOVIE, "Kids Anime").copy(fsk = "6", anime = true),
                ),
            )
            val vm = catalogViewModel(repository, WatchStateFixture(listOf(MIA)).repository)
            vm.state.test {
                awaitItem()
                val kidsView = awaitItem() as CatalogUiState.Ready
                val animeIds = kidsView.shelves.single { it.title == ANIME }.entries.filterIsInstance<Entry.Film>().map { it.set.setId }
                assertEquals(setOf("Kids Anime"), animeIds.toSet())
            }
        }

    @Test
    fun aKidsProfileWithNothingAllowedSaysWhatItIsWaitingFor() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository = FakeCatalogRepository(given = listOf(fakeSet(Kind.MOVIE, "Grown").copy(fsk = "16")))
            val vm = catalogViewModel(repository, WatchStateFixture(listOf(MIA)).repository)
            vm.state.test {
                awaitItem()
                assertEquals(CatalogUiState.KidsEmpty(12), awaitItem())
            }
        }

    /**
     * A kid at 6 sees only what is rated 6 or under and marked from 6; its
     * limit changed in Manage refilters the shelves it is already on, without
     * reading the library again.
     */
    @Test
    fun aKidAtSixSeesItsOwnLimitAndAChangedLimitRefiltersWithoutARead() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository =
                FakeCatalogRepository(
                    given =
                        listOf(
                            fakeSet(Kind.MOVIE, "Family").copy(fsk = "6"),
                            fakeSet(Kind.MOVIE, "Teen").copy(fsk = "12"),
                            fakeSet(Kind.MOVIE, "FromSix"),
                            fakeSet(Kind.MOVIE, "FromTwelve"),
                        ),
                )
            val admin = Profile("a", "Ana", admin = true)
            val kid = Profile("k", "Mia", kids = true, kidsAge = 6, parentId = "a")
            val watch =
                WatchStateFixture(listOf(kid, admin)) {
                    setKids("FromSix", 6)
                    setKids("FromTwelve", 12)
                }
            watch.core.roles.pins["a"] = "1234"
            val vm = catalogViewModel(repository, watch.repository)
            vm.state.test {
                awaitItem()
                val atSix = awaitItem() as CatalogUiState.Ready
                assertEquals(setOf("Family", "FromSix"), atSix.filmIds())
                val reads = repository.reads

                watch.repository.manage(ProfileRequest.SetKidsAge("a", "1234", "k", 12))

                val atTwelve = awaitItem() as CatalogUiState.Ready
                assertEquals(setOf("Family", "Teen", "FromSix", "FromTwelve"), atTwelve.filmIds())
                assertEquals(reads, repository.reads)
            }
        }

    @Test
    fun anEmptyShelfNamesTheKidsOwnLimit() {
        assertEquals("Nothing rated FSK 6 or under yet.", CatalogUiState.KidsEmpty(6).message)
    }

    private fun CatalogUiState.Ready.filmIds() =
        shelves
            .flatMap { it.entries }
            .filterIsInstance<Entry.Film>()
            .map { it.set.setId }
            .toSet()

    /**
     * A snapshot change that leaves the kids projection at [CatalogUiState.KidsEmpty]
     * both before and after must not surface as a second, equal emission —
     * [state] is a [kotlinx.coroutines.flow.StateFlow] and a StateFlow must
     * never emit the same value twice in a row.
     */
    @Test
    fun aKidsEmptyProjectionDoesNotRepeatOnAnUnrelatedSnapshotChange() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository = FakeCatalogRepository(given = listOf(fakeSet(Kind.MOVIE, "Grown").copy(fsk = "16")))
            val watch = WatchStateFixture(listOf(MIA))
            val vm = catalogViewModel(repository, watch.repository)
            vm.state.test {
                awaitItem()
                assertEquals(CatalogUiState.KidsEmpty(12), awaitItem())

                // Changes the catalog's watch payload, not which titles are kids-marked —
                // the projection stays KidsEmpty, so this must not re-emit it.
                watch.repository.setProgress("movie-0", 30.0, 3_600.0)
                advanceUntilIdle()

                expectNoEvents()
            }
        }

    /**
     * The picker takes the library out of composition while it is shown; if
     * nobody was collecting for more than the five-second `WhileSubscribed`
     * window, a switch to a kids profile must not surface as the adult's
     * shelves for even the first frame once collection resumes.
     */
    @Test
    fun switchingToAKidsProfileAfterAGapNeverShowsAnotherProfilesShelvesFirst() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository =
                FakeCatalogRepository(
                    given =
                        listOf(
                            fakeSet(Kind.MOVIE, "Family").copy(fsk = "6"),
                            fakeSet(Kind.MOVIE, "Grown").copy(fsk = "16"),
                        ),
                )
            val watch = WatchStateFixture(listOf(ANA, MIA))
            val vm = catalogViewModel(repository, watch.repository)

            vm.state.test {
                awaitItem()
                val adultView = awaitItem() as CatalogUiState.Ready
                val all = adultView.shelves.flatMap { it.entries }.filterIsInstance<Entry.Film>().map { it.set.setId }
                assertEquals(setOf("Family", "Grown"), all.toSet())
                cancelAndIgnoreRemainingEvents()
            }

            advanceTimeBy(6_000)
            runCurrent()

            watch.repository.chooseProfile(MIA.id)

            vm.state.test {
                val first = awaitItem()
                val ids =
                    when (first) {
                        is CatalogUiState.Ready ->
                            first.shelves.flatMap { it.entries }.filterIsInstance<Entry.Film>().map { it.set.setId }.toSet()
                        is CatalogUiState.KidsEmpty -> emptySet()
                        else -> error("unexpected first item after resubscribing: $first")
                    }
                assertEquals(setOf("Family"), ids)
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * `heldEventApplied` (fed by `SeriesPreloading`/`FilmPreloading.heldEvents`)
     * only ever adds a badge; a film the preloader removes needs its own
     * signal to drop one, which is what `FilmPreloading.unheldEvents` and
     * `heldEventRemoved` are for.
     */
    @Test
    fun aFilmThePreloaderRemovesDropsItsHeldBadge() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val filmPreloader = FakeFilmPreloading()
            val vm = catalogViewModel(FakeCatalogRepository(movies = 1), WatchStateFixture().repository, filmPreloader = filmPreloader)
            vm.state.test {
                awaitItem()
                val before = awaitItem() as CatalogUiState.Ready
                assertTrue(before.heldIds.isEmpty())

                filmPreloader.emitHeld("movie-0")
                val held = awaitItem() as CatalogUiState.Ready
                assertEquals(setOf("movie-0"), held.heldIds)

                filmPreloader.emitUnheld("movie-0")
                val unheld = awaitItem() as CatalogUiState.Ready
                assertTrue(unheld.heldIds.isEmpty())
            }
        }
}

package catalog

import app.cash.turbine.test
import data.CatalogEnrichmentFetcher
import data.CatalogRepository
import data.CoreClient
import data.LibraryEvents
import data.LibraryUpdateCoordinator
import data.WatchStateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import model.ListOfSets
import model.Profile
import model.Progress
import model.WatchSnapshot
import org.junit.After
import settings.InMemoryTmdbSettings
import uniffi.mediagram_core.FetchReport
import uniffi.mediagram_core.LibraryEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun catalogViewModel(
    repository: CatalogRepository,
    watchState: WatchStateRepository,
    enrichment: CatalogEnrichmentFetcher = CatalogEnrichmentFetcher(CatalogCoreProvider(CatalogCore()), InMemoryTmdbSettings()),
    events: LibraryEvents = LibraryEvents.None,
): CatalogViewModel = CatalogViewModel(repository, watchState, LibraryUpdateCoordinator(repository, enrichment), events)

/**
 * A snapshot this test can hold still — [CatalogViewModel] reads [snapshot]
 * for [CatalogUiState.Ready.watch] and writes the four list operations
 * through the rest; everything else here is an unused stub, [chooseProfile] and
 * [createProfile] included. Named apart from [catalog.profile.ProfileViewModelTest]'s own fake for
 * the same interface: Kotlin does not let two private top-level classes in
 * the same package share a name, file scope or not.
 *
 * The four writes land in [snapshot] itself, the same way the real
 * repository's own write lands in its snapshot before [CatalogViewModel]
 * ever asks again — a test asserting on [CatalogUiState.Ready.watch] after
 * one of them needs nothing more than that flow to already read the answer.
 */
private class FakeCatalogWatchState(
    watch: WatchSnapshot = WatchSnapshot.Empty,
) : WatchStateRepository {
    override val profiles = MutableStateFlow(emptyList<Profile>())
    override val chosenProfileId = MutableStateFlow<String?>(null)
    override val snapshot = MutableStateFlow(watch)

    /** Every list write this fake was asked for, in order, e.g. `"createList Favourites"`. */
    val calls = mutableListOf<String>()
    var writeFailure: Exception? = null
    var refuseWrites: Boolean = false

    override suspend fun reload() = Unit

    override fun invalidate() {
        profiles.value = emptyList()
        chosenProfileId.value = null
        snapshot.value = WatchSnapshot.Empty
    }

    override suspend fun chooseProfile(id: String) = false

    override suspend fun createProfile(name: String): Profile? = null

    override suspend fun setProgress(
        setId: String,
        at: Double,
        duration: Double?,
    ) = Unit

    override suspend fun clearProgress(setId: String) = Unit

    override suspend fun setWatched(
        setId: String,
        finished: Boolean,
    ) = Unit

    override suspend fun setWatchlisted(
        setId: String,
        listed: Boolean,
    ) = Unit

    override suspend fun setKids(
        setId: String,
        marked: Boolean,
    ) = Unit

    override suspend fun createList(name: String): ListOfSets? {
        writeFailure?.let { throw it }
        if (refuseWrites) return null
        calls += "createList $name"
        val made = ListOfSets(id = "list-${snapshot.value.collections.size + 1}", name = name, items = emptyList())
        snapshot.value = snapshot.value.copy(collections = snapshot.value.collections + made)
        return made
    }

    override suspend fun renameList(
        id: String,
        name: String,
    ): Boolean {
        writeFailure?.let { throw it }
        if (refuseWrites) return false
        calls += "renameList $id $name"
        if (snapshot.value.collections.none { it.id == id }) return false
        snapshot.value =
            snapshot.value.copy(
                collections = snapshot.value.collections.map { if (it.id == id) it.copy(name = name) else it },
            )
        return true
    }

    override suspend fun deleteList(id: String): Boolean {
        writeFailure?.let { throw it }
        if (refuseWrites) return false
        calls += "deleteList $id"
        if (snapshot.value.collections.none { it.id == id }) return false
        snapshot.value = snapshot.value.copy(collections = snapshot.value.collections.filterNot { it.id == id })
        return true
    }

    override suspend fun setInList(
        id: String,
        setId: String,
        included: Boolean,
    ): Boolean {
        writeFailure?.let { throw it }
        if (refuseWrites) return false
        calls += "setInList $id $setId $included"
        if (snapshot.value.collections.none { it.id == id }) return false
        snapshot.value =
            snapshot.value.copy(
                collections =
                    snapshot.value.collections.map { list ->
                        if (list.id != id) return@map list
                        list.copy(items = if (included) list.items + setId else list.items - setId)
                    },
            )
        return true
    }
}

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
            val vm = catalogViewModel(repository, FakeCatalogWatchState())
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
            val vm = catalogViewModel(repository, FakeCatalogWatchState())
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
            val vm = catalogViewModel(repository, FakeCatalogWatchState())
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
                object : CoreClient by CatalogCore() {
                    override suspend fun fetchMissing(
                        tmdbKey: String,
                        language: String,
                    ): FetchReport {
                        fetches += 1
                        repository.postersArrived = true
                        return FetchReport(1u, 0u, 0u, 0u, 0u, 0u)
                    }
                }
            val enrichment = CatalogEnrichmentFetcher(CatalogCoreProvider(core), InMemoryTmdbSettings().apply { write("key") })
            val vm = catalogViewModel(repository, FakeCatalogWatchState(), enrichment)
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
                            .single()
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
                object : CoreClient by CatalogCore() {
                    override suspend fun fetchMissing(
                        tmdbKey: String,
                        language: String,
                    ): FetchReport {
                        fetches += 1
                        return FetchReport(0u, 0u, 0u, 0u, 0u, 0u)
                    }
                }
            val enrichment = CatalogEnrichmentFetcher(CatalogCoreProvider(core), InMemoryTmdbSettings().apply { write("key") })
            val vm = catalogViewModel(repository, FakeCatalogWatchState(), enrichment)
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
            val watch = FakeCatalogWatchState().apply { writeFailure = IllegalStateException("private-storage-detail") }
            val vm = catalogViewModel(FakeCatalogRepository(movies = 1), watch)
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
            val watch = FakeCatalogWatchState().apply { refuseWrites = true }
            val vm = catalogViewModel(FakeCatalogRepository(movies = 1), watch)
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
            val watch = FakeCatalogWatchState().apply { writeFailure = IllegalStateException("cannot write") }
            val vm = catalogViewModel(FakeCatalogRepository(movies = 1), watch)
            vm.state.test {
                awaitItem()
                awaitItem()
                vm.createList("Favourite")
                assertEquals("Could not create the collection. Please try again.", (awaitItem() as CatalogUiState.Ready).notice)
                watch.writeFailure = null
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
            val vm = catalogViewModel(FakeCatalogRepository(movies = 1, refreshFails = true), FakeCatalogWatchState())
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
            val watch = FakeCatalogWatchState().apply { writeFailure = CancellationException("cancelled") }
            val vm = catalogViewModel(FakeCatalogRepository(movies = 1), watch)
            vm.state.test {
                awaitItem()
                val before = awaitItem() as CatalogUiState.Ready
                vm.createList("Favourite")
                runCurrent()
                expectNoEvents()
                assertEquals(before, vm.state.value)
                watch.writeFailure = null
                vm.createList("Favourite")
                assertEquals(listOf("Favourite"), (awaitItem() as CatalogUiState.Ready).watch.collections.map { it.name })
            }
        }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun shelvesAreGroupedByKind() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val vm = catalogViewModel(FakeCatalogRepository(movies = 2, episodes = 1, tutorials = 0), FakeCatalogWatchState())
            vm.state.test {
                assertEquals(CatalogUiState.Loading, awaitItem())
                val ready = awaitItem() as CatalogUiState.Ready
                assertEquals(listOf("Movies", "Series"), ready.shelves.map { it.title })
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
            val vm = catalogViewModel(FakeCatalogRepository(movies = 2, refreshFails = true), FakeCatalogWatchState())
            vm.state.test {
                awaitItem()
                val ready = awaitItem() as CatalogUiState.Ready
                assertEquals(listOf("Movies"), ready.shelves.map { it.title })
            }
        }

    /** Kept, but not quietly: a library that stopped updating has to say so. */
    @Test
    fun aRefreshThatFailedIsSaidRatherThanSwallowed() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val vm = catalogViewModel(FakeCatalogRepository(movies = 1, refreshFails = true), FakeCatalogWatchState())
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
            val vm = catalogViewModel(repository, FakeCatalogWatchState())
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
            val vm = catalogViewModel(repository, FakeCatalogWatchState())
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
            val vm = catalogViewModel(FakeCatalogRepository(refreshFails = true, onDisk = false), FakeCatalogWatchState())
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
            val vm = catalogViewModel(repository, FakeCatalogWatchState()) { pushed }
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
                    object : CoreClient by CatalogCore() {
                        override suspend fun fetchMissing(
                            tmdbKey: String,
                            language: String,
                        ): FetchReport {
                            fetches += 1
                            return FetchReport(0u, 0u, 0u, 0u, 0u, 0u)
                        }
                    }
                val enrichment = CatalogEnrichmentFetcher(CatalogCoreProvider(core), InMemoryTmdbSettings().apply { write("key") })
                val vm =
                    catalogViewModel(
                        FakeCatalogRepository(movies = 1, refreshFails = fails),
                        FakeCatalogWatchState(),
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
            val vm = catalogViewModel(repository, FakeCatalogWatchState())
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
            val watchState = FakeCatalogWatchState()
            val vm = catalogViewModel(repository, watchState)
            vm.state.test {
                awaitItem()
                val before = awaitItem() as CatalogUiState.Ready
                assertEquals(WatchSnapshot.Empty, before.watch)

                val progress = Progress(setId = "movie-0", at = 30.0, duration = 3_600.0, updatedAt = 1)
                watchState.snapshot.value = WatchSnapshot(listOf(progress), emptyList(), emptyList(), emptyList(), emptyList())

                val after = awaitItem() as CatalogUiState.Ready
                assertEquals(listOf(progress), after.watch.progress)
                assertEquals(1, repository.refreshes, "a changed snapshot is not a reason to read the channel again")
            }
        }

    /** The Collections tab's "New list", and its rename, delete and membership writes — each a fire-and-forget wrapper over the repository. */
    @Test
    fun createListReachesTheRepositoryAndTheNextSnapshot() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val watchState = FakeCatalogWatchState()
            val vm = catalogViewModel(FakeCatalogRepository(movies = 1), watchState)
            vm.state.test {
                awaitItem()
                awaitItem()

                vm.createList("Favourites")
                val after = awaitItem() as CatalogUiState.Ready

                assertEquals(listOf("Favourites"), after.watch.collections.map(ListOfSets::name))
                assertEquals(listOf("createList Favourites"), watchState.calls)
            }
        }

    @Test
    fun renameAndDeleteListReachTheRepository() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val watchState =
                FakeCatalogWatchState(
                    watch = WatchSnapshot.Empty.copy(collections = listOf(ListOfSets("l1", "Old name", emptyList()))),
                )
            val vm = catalogViewModel(FakeCatalogRepository(movies = 1), watchState)
            vm.state.test {
                awaitItem()
                awaitItem()

                vm.renameList("l1", "New name")
                assertEquals(listOf("New name"), (awaitItem() as CatalogUiState.Ready).watch.collections.map(ListOfSets::name))

                vm.deleteList("l1")
                assertTrue((awaitItem() as CatalogUiState.Ready).watch.collections.isEmpty())
            }
        }

    @Test
    fun setInListFilesAndRemovesATitle() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val watchState =
                FakeCatalogWatchState(
                    watch = WatchSnapshot.Empty.copy(collections = listOf(ListOfSets("l1", "Favourites", emptyList()))),
                )
            val vm = catalogViewModel(FakeCatalogRepository(movies = 1), watchState)
            vm.state.test {
                awaitItem()
                awaitItem()

                vm.setInList("l1", "movie-0", true)
                assertEquals(
                    listOf("movie-0"),
                    (awaitItem() as CatalogUiState.Ready)
                        .watch.collections
                        .single()
                        .items,
                )

                vm.setInList("l1", "movie-0", false)
                assertTrue(
                    (awaitItem() as CatalogUiState.Ready)
                        .watch.collections
                        .single()
                        .items
                        .isEmpty(),
                )
            }
        }
}

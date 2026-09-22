package catalog

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import uniffi.mediagram_core.LibraryEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Uses a standard (queued, not eager) test dispatcher tied to the same
 * scheduler as [runTest], not the unconfined one most ViewModel tests
 * reach for: the refresh this ViewModel launches at construction must
 * still be queued, not already run, by the time the state flow gets its
 * first subscriber, or the intermediate [CatalogUiState.Loading] value is
 * never observed.
 */
class CatalogViewModelTest {

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun shelvesAreGroupedByKind() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = CatalogViewModel(FakeCatalogRepository(movies = 2, episodes = 1, tutorials = 0))
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
    fun aFailedRefreshKeepsTheLibraryAlreadyOnThisDevice() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = CatalogViewModel(FakeCatalogRepository(movies = 2, refreshFails = true))
        vm.state.test {
            awaitItem()
            val ready = awaitItem() as CatalogUiState.Ready
            assertEquals(listOf("Movies"), ready.shelves.map { it.title })
        }
    }

    /** Kept, but not quietly: a library that stopped updating has to say so. */
    @Test
    fun aRefreshThatFailedIsSaidRatherThanSwallowed() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = CatalogViewModel(FakeCatalogRepository(movies = 1, refreshFails = true))
        vm.state.test {
            awaitItem()
            assertEquals("refresh failed", (awaitItem() as CatalogUiState.Ready).notice)
        }
    }

    /**
     * The whole point of the menu action. The state flow used to be a cold
     * flow handed straight to `stateIn`, so it ran once per subscription
     * and never again — asking for the library a second time was something
     * only a force-stop could do.
     */
    @Test
    fun askingAgainReadsTheChannelAgain() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeCatalogRepository(movies = 2)
        val vm = CatalogViewModel(repository)
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
    fun askingAgainKeepsTheShelvesItIsAboutToReplace() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = CatalogViewModel(FakeCatalogRepository(movies = 2))
        vm.state.test {
            assertEquals(CatalogUiState.Loading, awaitItem())
            val before = awaitItem() as CatalogUiState.Ready
            assertFalse(before.refreshing)

            vm.reload()

            val during = awaitItem() as CatalogUiState.Ready
            assertTrue(during.refreshing)
            assertEquals(before.shelves, during.shelves)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedRefreshWithNothingOnDiskSurfacesAsFailed() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = CatalogViewModel(FakeCatalogRepository(refreshFails = true, onDisk = false))
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
    fun aNewIndexFromAnotherDeviceReadsTheChannelAgain() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val pushed = MutableSharedFlow<LibraryEvent>()
        val repository = FakeCatalogRepository(movies = 2)
        val vm = CatalogViewModel(repository) { pushed }
        vm.state.test {
            assertEquals(CatalogUiState.Loading, awaitItem())
            awaitItem() as CatalogUiState.Ready
            assertEquals(1, repository.refreshes)

            pushed.emit(LibraryEvent.STATE)
            runCurrent()
            assertEquals(1, repository.refreshes, "watch state is not a catalog change")

            pushed.emit(LibraryEvent.INDEX)
            assertTrue((awaitItem() as CatalogUiState.Ready).refreshing)
            assertFalse((awaitItem() as CatalogUiState.Ready).refreshing)
            assertEquals(2, repository.refreshes)
        }
    }

    /**
     * New media from another device arrives with no artwork or descriptions
     * here, so a pushed read that brought it home asks for the fetch. A read
     * the button asked for does not — the button chains its own — and a read
     * that failed brought nothing home to describe.
     */
    @Test
    fun onlyAPushedReadThatSucceededAsksForAFetch() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val pushed = MutableSharedFlow<LibraryEvent>()
        val vm = CatalogViewModel(FakeCatalogRepository(movies = 1)) { pushed }
        val asked = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.published.toList(asked) }
        vm.state.test {
            awaitItem()
            awaitItem()

            vm.reload()
            awaitItem()
            awaitItem()
            assertEquals(0, asked.size, "a read the button asked for chains its own fetch")

            pushed.emit(LibraryEvent.INDEX)
            awaitItem()
            awaitItem()
            runCurrent()
            assertEquals(1, asked.size)
        }
    }

    @Test
    fun aPushedReadThatFailedAsksForNothing() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val pushed = MutableSharedFlow<LibraryEvent>()
        val vm = CatalogViewModel(FakeCatalogRepository(movies = 1, refreshFails = true)) { pushed }
        val asked = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.published.toList(asked) }
        vm.state.test {
            awaitItem()
            awaitItem()

            pushed.emit(LibraryEvent.INDEX)
            awaitItem()
            awaitItem()
            runCurrent()
            assertEquals(0, asked.size)
        }
    }

    /**
     * The defect this closes: a card looks its poster up when the shelves are
     * built, and a fetch finishes after they are, so fetched artwork sat on
     * disk behind initials until the next reload. Showing it must not ask the
     * channel again, and must not flag the library as refreshing.
     */
    @Test
    fun artworkAFetchLaidDownIsShownWithoutAskingTheChannel() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeCatalogRepository(movies = 1)
        val vm = CatalogViewModel(repository)
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
}

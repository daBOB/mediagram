package catalog

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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
}

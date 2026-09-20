package catalog

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
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

    /** A reload puts the shelves back to loading, so the screen is not left showing a stale library as current. */
    @Test
    fun askingAgainSaysItIsLoadingBeforeItAnswers() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = CatalogViewModel(FakeCatalogRepository(movies = 2))
        vm.state.test {
            awaitItem()
            awaitItem() as CatalogUiState.Ready

            vm.reload()

            assertEquals(CatalogUiState.Loading, awaitItem())
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
}

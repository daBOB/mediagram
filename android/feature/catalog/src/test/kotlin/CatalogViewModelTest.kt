package catalog

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
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

    @Test
    fun aFailedRefreshSurfacesAsFailed() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = CatalogViewModel(FakeCatalogRepository(refreshFails = true))
        vm.state.test {
            awaitItem()
            assertTrue(awaitItem() is CatalogUiState.Failed)
        }
    }
}

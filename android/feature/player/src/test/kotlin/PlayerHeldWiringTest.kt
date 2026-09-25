package player

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import model.Kind
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Covers [PlayerViewModel.held] — the stats overlay's "cached" line. */
class PlayerHeldWiringTest {

    private val episode = fakeMediaSet("e1", kind = Kind.EPISODE, totalBytes = 1_000)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun trueOnceTheOpenTitleIsFoundHeld() = runTest {
        installMainDispatcher()
        val catalogRepository = FakeCatalogRepository(mapOf(episode.setId to episode))
        val vm = buildViewModel(catalogRepository = catalogRepository, heldSets = FakeHeldSets(held = setOf("e1")))

        vm.held.test {
            assertFalse(awaitItem())
            vm.open("e1")
            advanceUntilIdle()
            assertTrue(awaitItem())
        }
    }

    @Test
    fun falseForATitleNotHeld() = runTest {
        installMainDispatcher()
        val catalogRepository = FakeCatalogRepository(mapOf(episode.setId to episode))
        val vm = buildViewModel(catalogRepository = catalogRepository, heldSets = FakeHeldSets(held = emptySet()))

        vm.open("e1")
        advanceUntilIdle()

        vm.held.test { assertFalse(awaitItem()) }
    }

    /** A preload finishing the very title now open flips this without waiting for a rescan. */
    @Test
    fun trueAsSoonAsAPreloadHeldEventNamesTheOpenTitle() = runTest {
        installMainDispatcher()
        val catalogRepository = FakeCatalogRepository(mapOf(episode.setId to episode))
        val preloader = FakeSeriesPreloader()
        val vm = buildViewModel(catalogRepository = catalogRepository, seriesPreloader = preloader, heldSets = FakeHeldSets())
        vm.open("e1")
        advanceUntilIdle()

        vm.held.test {
            assertFalse(awaitItem())
            preloader.emitHeld("e1")
            assertTrue(awaitItem())
        }
    }
}

package player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import model.Kind
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers [PlayerViewModelOpen.open] asking [SeriesPreloading] for what
 * follows an opened episode — the wiring behind [PlayerPreloadController],
 * which has its own unit coverage for the walk itself.
 */
class PlayerPreloadWiringTest {

    private val e1 = fakeMediaSet("e1", kind = Kind.EPISODE, totalBytes = 1_000)
    private val e2 = fakeMediaSet("e2", kind = Kind.EPISODE, totalBytes = 2_000)
    private val e3 = fakeMediaSet("e3", kind = Kind.EPISODE, totalBytes = 3_000)
    private val film = fakeMediaSet("f1", kind = Kind.MOVIE, totalBytes = 5_000)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun openingAnEpisodeAsksForTheNextTwoFromItsOwnRun() = runTest {
        installMainDispatcher()
        val preloader = FakeSeriesPreloader()
        val catalogRepository = FakeCatalogRepository(mapOf(e1.setId to e1, e2.setId to e2, e3.setId to e3))
        val vm = buildViewModel(catalogRepository = catalogRepository, seriesPreloader = preloader)

        vm.open("e1", listOf("e1", "e2", "e3"))
        advanceUntilIdle()

        assertEquals(1, preloader.wantCalls.size)
        val (items, currentBytes) = preloader.wantCalls.single()
        assertEquals(listOf("e2", "e3"), items.map { it.setId })
        assertEquals(1_000L, currentBytes)
    }

    @Test
    fun openingAFilmAsksForNothing() = runTest {
        installMainDispatcher()
        val preloader = FakeSeriesPreloader()
        val catalogRepository = FakeCatalogRepository(mapOf(film.setId to film))
        val vm = buildViewModel(catalogRepository = catalogRepository, seriesPreloader = preloader)

        vm.open("f1", listOf("f1"))
        advanceUntilIdle()

        assertTrue(preloader.wantCalls.isEmpty())
    }

    @Test
    fun openingTheLastEpisodeOfARunAsksForNothing() = runTest {
        installMainDispatcher()
        val preloader = FakeSeriesPreloader()
        val catalogRepository = FakeCatalogRepository(mapOf(e1.setId to e1, e2.setId to e2))
        val vm = buildViewModel(catalogRepository = catalogRepository, seriesPreloader = preloader)

        vm.open("e2", listOf("e1", "e2"))
        advanceUntilIdle()

        assertTrue(preloader.wantCalls.isEmpty())
    }
}

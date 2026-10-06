package player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import model.Kind
import org.junit.After
import testing.FakeSeriesPreloader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers [PlayerViewModelOpen.open] asking [SeriesPreloading] for what
 * follows an opened episode, by the web's `playsNext` rule: the next two
 * positions of its show that are episodes, and nothing from a hand-picked
 * run (a list or the Kids wall).
 */
class PlayerPreloadWiringTest {

    private val e1 = fakeMediaSet("e1", kind = Kind.EPISODE, totalBytes = 1_000)
    private val e2 = fakeMediaSet("e2", kind = Kind.EPISODE, totalBytes = 2_000)
    private val e3 = fakeMediaSet("e3", kind = Kind.EPISODE, totalBytes = 3_000)
    private val e4 = fakeMediaSet("e4", kind = Kind.EPISODE, totalBytes = 4_000)
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

    @Test
    fun anEpisodePlayedFromAListAsksForNothing() = runTest {
        installMainDispatcher()
        val preloader = FakeSeriesPreloader()
        val catalogRepository = FakeCatalogRepository(mapOf(e1.setId to e1, e2.setId to e2, e3.setId to e3))
        val vm = buildViewModel(catalogRepository = catalogRepository, seriesPreloader = preloader)

        vm.open("e1", listOf("e1", "e2", "e3"), handPicked = true)
        advanceUntilIdle()

        assertTrue(preloader.wantCalls.isEmpty())
    }

    @Test
    fun onlyTheNextTwoPositionsAreConsideredAndOnlyEpisodesTaken() = runTest {
        installMainDispatcher()
        val preloader = FakeSeriesPreloader()
        val catalogRepository = FakeCatalogRepository(
            mapOf(e1.setId to e1, film.setId to film, e3.setId to e3, e4.setId to e4),
        )
        val vm = buildViewModel(catalogRepository = catalogRepository, seriesPreloader = preloader)

        // The film in the second place is left out, not replaced by e4.
        vm.open("e1", listOf("e1", "e3", "f1", "e4"))
        advanceUntilIdle()

        assertEquals(listOf("e3"), preloader.wantCalls.single().first.map { it.setId })
    }
}

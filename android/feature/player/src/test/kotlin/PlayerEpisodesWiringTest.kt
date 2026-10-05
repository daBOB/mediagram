package player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import model.Kind
import org.junit.After
import testing.WatchStateFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [PlayerViewModel.episodes] — the episode sidebar's list, kept current as the run and the watch state move. */
class PlayerEpisodesWiringTest {

    private val episodes = listOf(
        fakeMediaSet("e1", kind = Kind.EPISODE, show = "A Show", season = 1, episodeFirst = 1, durationSecs = 1_200),
        fakeMediaSet("e2", kind = Kind.EPISODE, show = "A Show", season = 1, episodeFirst = 2, durationSecs = 1_200),
        fakeMediaSet("e3", kind = Kind.EPISODE, show = "A Show", season = 2, episodeFirst = 1, durationSecs = 1_200),
    )
    private val catalog = FakeCatalogRepository(episodes.associateBy { it.setId })
    private val run = episodes.map { it.setId }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun anEpisodeOpenedInItsRunListsTheShowOnItsOwnSeason() = runTest {
        installMainDispatcher()
        val vm = buildViewModel(catalogRepository = catalog)

        vm.open("e3", run)
        advanceUntilIdle()

        val list = assertNotNull(vm.episodes.value)
        assertEquals(listOf("Season 1", "Season 2"), list.sections.map { it.title })
        assertEquals(1, list.currentSection)
        assertTrue(list.sections[1].rows.single().current)
    }

    @Test
    fun aFilmWithNoRunHasNone() = runTest {
        installMainDispatcher()
        val vm = buildViewModel(catalogRepository = FakeCatalogRepository(mapOf("f1" to fakeMediaSet("f1"))))

        vm.open("f1")
        advanceUntilIdle()

        assertNull(vm.episodes.value)
    }

    /** Finishing a title — here, or on another device a sync just brought in — re-ticks its row without the sidebar being reopened. */
    @Test
    fun aTitleFinishedWhileOpenIsTickedAtOnce() = runTest {
        installMainDispatcher()
        val watch = WatchStateFixture()
        val vm = buildViewModel(repository = watch.repository, catalogRepository = catalog)
        vm.open("e2", run)
        advanceUntilIdle()

        watch.repository.setWatched("e1", true)
        advanceUntilIdle()

        assertTrue(assertNotNull(vm.episodes.value).sections[0].rows[0].watched)
    }

    /** The catalogue finishing its own load after the title opened hands the run over late; the list follows it. */
    @Test
    fun aRunHandedOverLateBuildsTheList() = runTest {
        installMainDispatcher()
        val vm = buildViewModel(catalogRepository = catalog)
        vm.open("e1")
        advanceUntilIdle()
        assertNull(vm.episodes.value)

        vm.updateRun("e1", run)
        advanceUntilIdle()

        assertEquals(3, assertNotNull(vm.episodes.value).sections.sumOf { it.rows.size })
    }

    @Test
    fun leavingThePlayerClearsTheList() = runTest {
        installMainDispatcher()
        val vm = buildViewModel(catalogRepository = catalog)
        vm.open("e1", run)
        advanceUntilIdle()

        vm.stop()
        advanceUntilIdle()

        assertNull(vm.episodes.value)
    }
}

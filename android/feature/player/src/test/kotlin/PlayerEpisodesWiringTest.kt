package player

import kotlinx.coroutines.CompletableDeferred
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

    private val film = fakeMediaSet("f1")
    private val otherShow = fakeMediaSet("z1", kind = Kind.EPISODE, show = "Another Show", season = 1, episodeFirst = 1)

    /** A list built by hand (My List, the Kids wall) mixes shows, and the web hides the sidebar for it. */
    @Test
    fun aHandBuiltListGetsNoEpisodeList() = runTest {
        installMainDispatcher()
        val everything = (episodes + film + otherShow).associateBy { it.setId }
        val mixed = listOf("e1", "f1", "z1")

        val fromList = buildViewModel(catalogRepository = FakeCatalogRepository(everything))
        fromList.open("e1", mixed, handPicked = true)
        advanceUntilIdle()
        assertNull(fromList.episodes.value)

        val fromShow = buildViewModel(catalogRepository = FakeCatalogRepository(everything))
        fromShow.open("e1", run, handPicked = false)
        advanceUntilIdle()
        assertNotNull(fromShow.episodes.value)
    }

    /** Rows never say "Unknown title" because the catalogue has not answered yet. */
    @Test
    fun noListUntilTheCatalogueHasAnswered() = runTest {
        installMainDispatcher()
        val gate = CompletableDeferred<Unit>()
        val vm = buildViewModel(catalogRepository = FakeCatalogRepository(episodes.associateBy { it.setId }, setsGate = gate))

        vm.open("e1", run)
        advanceUntilIdle()
        assertNull(vm.episodes.value)

        gate.complete(Unit)
        advanceUntilIdle()

        val rows = assertNotNull(vm.episodes.value).sections.flatMap { it.rows }
        assertTrue(rows.none { it.title == UNKNOWN_TITLE })
    }

    @Test
    fun aFilmNeverGetsAListEvenWhenTheReadFails() = runTest {
        installMainDispatcher()
        val failing = FakeCatalogRepository(mapOf("f1" to film)).apply { failSets = true }
        val vm = buildViewModel(catalogRepository = failing)

        vm.open("f1", listOf("f1", "f2"))
        advanceUntilIdle()

        assertNull(vm.episodes.value)
    }

    @Test
    fun aFailedLaterReadKeepsThePreviousListing() = runTest {
        installMainDispatcher()
        val repo = FakeCatalogRepository(episodes.associateBy { it.setId })
        val vm = buildViewModel(catalogRepository = repo)
        vm.open("e1", run)
        advanceUntilIdle()

        repo.failSets = true
        vm.updateRun("e1", run + "ghost") // an id the listing lacks asks for a fresh read
        advanceUntilIdle()

        val rows = assertNotNull(vm.episodes.value).sections.flatMap { it.rows }
        assertEquals("e1", rows.first().setId)
        assertEquals("S1E1", rows.first().number)
    }

    /** Only the run's own titles are kept, not the whole catalogue. */
    @Test
    fun onlyTheRunsTitlesAreKept() {
        val all = episodes + film + otherShow

        assertEquals(setOf("e1", "e3"), runSetsOf(all, listOf("e1", "e3", "ghost")).keys)
    }
}

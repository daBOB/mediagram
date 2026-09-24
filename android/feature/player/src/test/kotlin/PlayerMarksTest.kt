package player

import app.cash.turbine.test
import data.WatchSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import model.KidsVerdict
import model.ListOfSets
import model.WatchSnapshot
import org.junit.After
import playback.PlaybackCounters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private object SilentWatchSync : WatchSync {
    override fun onForeground() = Unit
    override fun onBackground() = Unit
    override fun soon() = Unit
    override suspend fun awaitFirstRound() = Unit
}

/**
 * The player's three kept controls — Watchlist, Kids, Add to list — ported
 * from `player.js`'s click handlers: each writes through
 * [data.WatchStateRepository] and [PlayerViewModel.marks] picks the result
 * straight back up, the way the web's own buttons re-read their state off
 * `watch-state.js` after every write. [marks] is collected through turbine
 * throughout, since it is shared with `WhileSubscribed` and answers nothing
 * to a bare `.value` read with nobody collecting it. Assertions after a
 * write read [app.cash.turbine.ReceiveTurbine.expectMostRecentItem] rather
 * than a single `awaitItem()`: a write that touches the snapshot more than
 * once (`createListAndAdd`) may or may not coalesce its two emissions
 * depending on exactly when the collector is scheduled, and the number of
 * emissions along the way was never the thing worth asserting on.
 */
class PlayerMarksTest {

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(repository: FakeWatchStateRepository = FakeWatchStateRepository()) = PlayerViewModel(
        FakePlayerHandle(),
        PlaybackCounters(),
        repository,
        ProgressRecorder(repository),
        SilentWatchSync,
        FakeCatalogRepository(),
        FakePlayerPreferences(),
        FakeSubtitleTrackSource(),
    )

    @Test
    fun marksIsNullWithNothingOpen() = runTest {
        installMainDispatcher()
        viewModel().marks.test { assertNull(awaitItem()) }
    }

    @Test
    fun toggleWatchlistPutsTheOpenTitleOnAndOffTheList() = runTest {
        installMainDispatcher()
        val vm = viewModel()

        vm.marks.test {
            assertNull(awaitItem())
            vm.open("s1")
            assertEquals(false, awaitItem()?.watchlisted)

            vm.toggleWatchlist()
            advanceUntilIdle()
            assertEquals(true, expectMostRecentItem()?.watchlisted)

            vm.toggleWatchlist()
            advanceUntilIdle()
            assertEquals(false, expectMostRecentItem()?.watchlisted)
        }
    }

    @Test
    fun toggleKidsMarksAndUnmarksTheOpenTitle() = runTest {
        installMainDispatcher()
        val vm = viewModel()

        vm.marks.test {
            assertNull(awaitItem())
            vm.open("s1")
            assertEquals(false, awaitItem()?.kids)

            vm.toggleKids()
            advanceUntilIdle()
            assertEquals(true, expectMostRecentItem()?.kids)

            vm.toggleKids()
            advanceUntilIdle()
            assertEquals(false, expectMostRecentItem()?.kids)
        }
    }

    @Test
    fun aRatedTitleIsDecidedByItsRatingAndCannotBeMarked() = runTest {
        installMainDispatcher()
        val repository = FakeWatchStateRepository()
        val vm = viewModel(repository)

        vm.marks.test {
            assertNull(awaitItem())
            vm.open("s1", fsk = "16")
            val marks = awaitItem()
            assertEquals(KidsVerdict.UNSAFE, marks?.kidsVerdict)
            assertEquals("FSK 16", marks?.ageLabel)
            assertEquals(false, marks?.forKids)

            // Refused: nothing is written, so nothing changes.
            vm.toggleKids()
            advanceUntilIdle()
            expectNoEvents()
            assertTrue(repository.snapshot.value.kids.isEmpty())
        }
    }

    @Test
    fun aTitleRatedForKidsIsForKidsWithoutAMark() = runTest {
        installMainDispatcher()
        val vm = viewModel()

        vm.marks.test {
            assertNull(awaitItem())
            vm.open("s1", fsk = "6")
            val marks = awaitItem()
            assertEquals(KidsVerdict.SAFE, marks?.kidsVerdict)
            assertEquals(true, marks?.forKids)
            assertEquals(false, marks?.kids)
        }
    }

    @Test
    fun toggleWatchlistWithNothingOpenWritesNothing() = runTest {
        installMainDispatcher()
        val repository = FakeWatchStateRepository()
        val vm = viewModel(repository)

        vm.toggleWatchlist()
        advanceUntilIdle()

        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun setInListFilesTheOpenTitleAndMarksItThere() = runTest {
        installMainDispatcher()
        val repository = FakeWatchStateRepository(
            initialSnapshot = WatchSnapshot.Empty.copy(collections = listOf(ListOfSets("l1", "Favourites", emptyList()))),
        )
        val vm = viewModel(repository)

        vm.marks.test {
            assertNull(awaitItem())
            vm.open("s1")
            assertEquals(emptySet<String>(), awaitItem()?.memberOf)

            vm.setInList("l1", true)
            advanceUntilIdle()
            assertEquals(setOf("l1"), expectMostRecentItem()?.memberOf)
        }
    }

    @Test
    fun createListAndAddMakesTheListAndFilesTheOpenTitleOnIt() = runTest {
        installMainDispatcher()
        val vm = viewModel()

        vm.marks.test {
            assertNull(awaitItem())
            vm.open("s1")
            assertEquals(emptyList<ListOfSets>(), awaitItem()?.lists)

            vm.createListAndAdd("Favourites")
            advanceUntilIdle()
            val settled = requireNotNull(expectMostRecentItem())
            assertEquals(listOf("Favourites"), settled.lists.map(ListOfSets::name))
            assertEquals(setOf(settled.lists.single().id), settled.memberOf)
        }
    }
}

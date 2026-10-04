package player

import app.cash.turbine.test
import data.WatchStateRepository
import data.WatchSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import model.KidsVerdict
import model.ListOfSets
import model.Profile
import model.WatchSnapshot
import org.junit.After
import playback.PlaybackCounters
import testing.WatchStateFixture
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

    private fun viewModel(repository: WatchStateRepository = WatchStateFixture().repository) = PlayerViewModel(
        FakePlayerHandle(),
        PlaybackCounters(),
        repository,
        ProgressRecorder(repository),
        SilentWatchSync,
        FakeCatalogRepository(),
        FakePlayerPreferences(),
        FakeSubtitleTrackSource(),
        seriesPreloader = FakeSeriesPreloader(),
        heldSets = FakeHeldSets(),
    )

    @Test
    fun marksIsNullWithNothingOpen() =
        runTest {
            installMainDispatcher()
            viewModel().marks.test { assertNull(awaitItem()) }
        }

    @Test
    fun toggleWatchlistPutsTheOpenTitleOnAndOffTheList() =
        runTest {
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
    fun aKidsMarkIsSetAndClearedOnTheOpenTitle() =
        runTest {
            installMainDispatcher()
            val vm = viewModel()

            vm.marks.test {
                assertNull(awaitItem())
                vm.open("s1")
                assertNull(awaitItem()?.kidsMark)

                vm.setKidsMark(12)
                advanceUntilIdle()
                assertEquals(12, expectMostRecentItem()?.kidsMark)

                vm.setKidsMark(null)
                advanceUntilIdle()
                assertNull(expectMostRecentItem()?.kidsMark)
            }
        }

    @Test
    fun aRatedTitleIsDecidedByItsRatingAndCannotBeMarked() =
        runTest {
            installMainDispatcher()
            val repository = WatchStateFixture().repository
            val vm = viewModel(repository)

            vm.marks.test {
                assertNull(awaitItem())
                vm.open("s1", fsk = "16")
                val marks = awaitItem()
                assertEquals(KidsVerdict.UNSAFE, marks?.kidsVerdict)
                assertEquals("FSK 16", marks?.ageLabel)
                assertEquals(false, marks?.forKids)

                // Refused: nothing is written, so nothing changes.
                vm.setKidsMark(12)
                advanceUntilIdle()
                expectNoEvents()
                assertTrue(
                    repository.snapshot.value.kids
                        .isEmpty(),
                )
            }
        }

    @Test
    fun aTitleRatedForKidsIsForKidsWithoutAMark() =
        runTest {
            installMainDispatcher()
            val vm = viewModel()

            vm.marks.test {
                assertNull(awaitItem())
                vm.open("s1", fsk = "6")
                val marks = awaitItem()
                assertEquals(KidsVerdict.SAFE, marks?.kidsVerdict)
                assertEquals(true, marks?.forKids)
                assertEquals(true, marks?.forEveryKid)
                assertNull(marks?.kidsMark)
            }
        }

    @Test
    fun aKidsProfileCannotMarkTitlesForKids() =
        runTest {
            installMainDispatcher()
            val repository = WatchStateFixture(listOf(Profile("p1", "Mia", kids = true))).repository
            val vm = viewModel(repository)

            vm.marks.test {
                assertNull(awaitItem())
                vm.open("s1")
                assertEquals(false, awaitItem()?.canMarkKids)

                // Refused: nothing is written, so nothing changes.
                vm.setKidsMark(12)
                vm.setKidsMark(6)
                advanceUntilIdle()
                expectNoEvents()
                assertTrue(
                    repository.snapshot.value.kids
                        .isEmpty(),
                )
            }
        }

    /** The web's select: "Not for kids", "From 6", "From 12" — and no other age. */
    @Test
    fun theKidsChoiceMarksFromSixFromTwelveOrNotAtAll() =
        runTest {
            installMainDispatcher()
            val repository = WatchStateFixture().repository
            val vm = viewModel(repository)

            vm.marks.test {
                assertNull(awaitItem())
                vm.open("s1")
                assertNull(awaitItem()?.kidsMark)

                vm.setKidsMark(6)
                advanceUntilIdle()
                assertEquals(6, expectMostRecentItem()?.kidsMark)
                assertEquals(listOf("s1"), repository.snapshot.value.kidsFromSix)

                vm.setKidsMark(12)
                advanceUntilIdle()
                assertEquals(12, expectMostRecentItem()?.kidsMark)

                vm.setKidsMark(7)
                advanceUntilIdle()
                expectNoEvents()

                vm.setKidsMark(null)
                advanceUntilIdle()
                assertNull(expectMostRecentItem()?.kidsMark)
                assertEquals(emptyList(), repository.snapshot.value.kids)
            }
        }

    /** A grown-up reads a rating against the widest limit a kid can have: FSK 12 is for some kids, and is not chosen for. */
    @Test
    fun aTwelveIsForKidsFromTwelveAndCannotBeChosenFor() =
        runTest {
            installMainDispatcher()
            val repository = WatchStateFixture().repository
            val vm = viewModel(repository)

            vm.marks.test {
                assertNull(awaitItem())
                vm.open("s1", fsk = "12")
                val marks = awaitItem()
                assertEquals(KidsVerdict.SAFE, marks?.kidsVerdict)
                assertEquals(false, marks?.forEveryKid)
                vm.setKidsMark(6)
                advanceUntilIdle()
                expectNoEvents()
                assertTrue(repository.snapshot.value.kids.isEmpty())
            }
        }

    @Test
    fun toggleWatchlistWithNothingOpenWritesNothing() =
        runTest {
            installMainDispatcher()
            val repository = WatchStateFixture().repository
            val vm = viewModel(repository)

            vm.toggleWatchlist()
            advanceUntilIdle()

            assertEquals(WatchSnapshot.Empty, repository.snapshot.value)
        }

    @Test
    fun setInListFilesTheOpenTitleAndMarksItThere() =
        runTest {
            installMainDispatcher()
            val repository = WatchStateFixture(seed = { createList("Favourites") }).repository
            val favourites = repository.snapshot.value.collections.single().id
            val vm = viewModel(repository)

            vm.marks.test {
                assertNull(awaitItem())
                vm.open("s1")
                assertEquals(emptySet<String>(), awaitItem()?.memberOf)

                vm.setInList(favourites, true)
                advanceUntilIdle()
                assertEquals(setOf(favourites), expectMostRecentItem()?.memberOf)
            }
        }

    @Test
    fun createListAndAddMakesTheListAndFilesTheOpenTitleOnIt() =
        runTest {
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

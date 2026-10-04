package catalog.profile

import app.cash.turbine.test
import data.WatchSync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import model.Profile
import org.junit.After
import testing.WatchStateFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

private val ALICE = Profile("p1", "Alice")
private val BEA = Profile("p2", "Bea")

/** [settles] false stands in for a round that is still in flight when the picker asks. */
private class FakeWatchSync(
    private val settles: Boolean = true,
) : WatchSync {
    override fun onForeground() = Unit

    override fun onBackground() = Unit

    override fun soon() = Unit

    override suspend fun awaitFirstRound() {
        if (!settles) awaitCancellation()
    }
}

/**
 * Uses the same queued [StandardTestDispatcher] [CatalogViewModelTest] does,
 * not an eager one: [ProfileViewModel.state]'s own `onStart` runs
 * `settle()`, and the intermediate [ProfileUiState.Loading] value is only
 * ever observed if that suspends rather than finishing before the first
 * collection.
 *
 * Runs the real repository over the fake core ([WatchStateFixture]): a read
 * or write fails at the provider, the way the app's own do, and a refusal is
 * the core's own.
 */
class ProfileViewModelTest {
    @Test
    fun immediateReentryReconcilesARetainedViewModelAfterReset() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val watch = WatchStateFixture(listOf(ALICE))
            val readsBefore = watch.core.profilesCalls
            val vm = ProfileViewModel(watch.repository, FakeWatchSync())
            vm.state.test {
                awaitItem()
                assertEquals(ProfileUiState.Chosen(ALICE), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            runCurrent()
            // Setup has reset the account while this Activity's ViewModel survives:
            // the core starts over empty, and the repository forgets what it held.
            watch.core.profiles = emptyList()
            watch.core.chosen = null
            watch.repository.invalidate()
            runCurrent()
            vm.state.test {
                runCurrent()
                assertEquals(ProfileUiState.Picking(emptyList(), false), vm.state.value)
                assertEquals(2, watch.core.profilesCalls - readsBefore, "each entry reads the profiles again")
                cancelAndIgnoreRemainingEvents()
            }
        }

    /** Alice is already known: the fixture's own first read is the one an earlier screen would have made. */
    @Test
    fun anInitialReadFailureKeepsKnownProfilesAndExitsLoading() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val watch = WatchStateFixture(listOf(ALICE), chosen = null)
            watch.provider.beforeCore = { error("private path") }
            val vm = ProfileViewModel(watch.repository, FakeWatchSync())
            vm.state.test {
                awaitItem()
                assertEquals(ProfileUiState.Picking(listOf(ALICE), false, "Could not load profiles. Please try again."), awaitItem())
                watch.provider.beforeCore = {}
                vm.retry()
                runCurrent()
                assertEquals(ProfileUiState.Picking(listOf(ALICE), false), vm.state.value)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aFailedAddKeepsThePickerAndDoesNotInventAProfile() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val watch = WatchStateFixture(listOf(ALICE), chosen = null)
            val vm = ProfileViewModel(watch.repository, FakeWatchSync())
            vm.state.test {
                awaitItem()
                awaitItem()
                watch.provider.beforeCore = { error("private path") }
                vm.add("Bea", kids = false)
                assertEquals(ProfileUiState.Picking(listOf(ALICE), false, "Could not create the profile. Please try again."), awaitItem())
                assertEquals(listOf("Alice"), watch.core.profiles.map { it.name })
                assertEquals(null, watch.repository.chosenProfileId.value)
            }
        }

    @Test
    fun aFailedChoiceKeepsTheExistingProfileAndPicker() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val profiles = listOf(ALICE, BEA)
            val watch = WatchStateFixture(profiles)
            val vm = ProfileViewModel(watch.repository, FakeWatchSync())
            vm.state.test {
                awaitItem()
                awaitItem()
                vm.reopen()
                awaitItem()
                watch.provider.beforeCore = { error("private path") }
                vm.choose(BEA.id)
                assertEquals(ProfileUiState.Picking(profiles, true, "Could not choose that profile. Please try again."), awaitItem())
                vm.stay()
                assertEquals(ProfileUiState.Chosen(ALICE), awaitItem())
            }
        }

    @Test
    fun aFailedReloadKeepsTheExistingWayToStay() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val watch = WatchStateFixture(listOf(ALICE))
            val vm = ProfileViewModel(watch.repository, FakeWatchSync())
            vm.state.test {
                awaitItem()
                awaitItem()
                vm.reopen()
                awaitItem()
                watch.provider.beforeCore = { error("private path") }
                vm.retry()
                runCurrent()
                assertEquals(ProfileUiState.Picking(listOf(ALICE), true, "Could not load profiles. Please try again."), vm.state.value)
                cancelAndIgnoreRemainingEvents()
            }
        }

    /** The core refuses a name that is only whitespace, and a profile it does not have. */
    @Test
    fun refusedProfileWritesAreShownAsFailures() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val watch = WatchStateFixture(emptyList())
            val vm = ProfileViewModel(watch.repository, FakeWatchSync())
            vm.state.test {
                awaitItem()
                awaitItem()
                vm.add("   ", kids = false)
                assertEquals("Could not create the profile. Please try again.", (awaitItem() as ProfileUiState.Picking).error)
                vm.choose("p1")
                assertEquals("Could not choose that profile. Please try again.", (awaitItem() as ProfileUiState.Picking).error)
            }
        }

    @Test
    fun cancelledProfileWritesLeaveThePickerUnchanged() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val watch = WatchStateFixture(emptyList())
            val vm = ProfileViewModel(watch.repository, FakeWatchSync())
            vm.state.test {
                awaitItem()
                val before = awaitItem()
                watch.provider.beforeCore = { throw CancellationException("cancelled") }
                vm.add("Bea", kids = false)
                vm.choose("p1")
                runCurrent()
                expectNoEvents()
                assertEquals(before, vm.state.value)
            }
        }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aProfileAlreadyChosenIsShownWithoutWaitingForASyncRound() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val watch = WatchStateFixture(listOf(ALICE))
            val vm = ProfileViewModel(watch.repository, FakeWatchSync(settles = false))

            vm.state.test {
                assertEquals(ProfileUiState.Loading, awaitItem())
                assertEquals(ProfileUiState.Chosen(ALICE), awaitItem())
            }
        }

    @Test
    fun nobodyChosenShowsThePickerOnceTheFirstRoundSettles() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val watch = WatchStateFixture(listOf(ALICE), chosen = null)
            val vm = ProfileViewModel(watch.repository, FakeWatchSync(settles = true))

            vm.state.test {
                assertEquals(ProfileUiState.Loading, awaitItem())
                val picking = awaitItem() as ProfileUiState.Picking
                assertEquals(listOf(ALICE), picking.profiles)
                assertEquals(false, picking.canStay, "nothing chosen yet, nothing to stay as")
            }
        }

    /** The 5-second cap: a round still running does not keep the picker off screen forever. */
    @Test
    fun aRoundStillRunningStopsBlockingThePickerAfterFiveSeconds() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val watch = WatchStateFixture(emptyList())
            val vm = ProfileViewModel(watch.repository, FakeWatchSync(settles = false))

            vm.state.test {
                assertEquals(ProfileUiState.Loading, awaitItem())
                advanceTimeBy(5.seconds)
                runCurrent()
                assertEquals(ProfileUiState.Picking(emptyList(), canStay = false), awaitItem())
            }
        }

    @Test
    fun choosingAKnownProfileShowsItAsChosen() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val watch = WatchStateFixture(listOf(ALICE), chosen = null)
            val vm = ProfileViewModel(watch.repository, FakeWatchSync())

            vm.state.test {
                awaitItem()
                awaitItem() as ProfileUiState.Picking

                vm.choose(ALICE.id)

                assertEquals(ProfileUiState.Chosen(ALICE), awaitItem())
            }
        }

    /** The bar action: reopens the picker over whoever was already chosen, with a way back. */
    @Test
    fun reopenShowsThePickerWithAWayToStay() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val watch = WatchStateFixture(listOf(ALICE, BEA))
            val vm = ProfileViewModel(watch.repository, FakeWatchSync())

            vm.state.test {
                awaitItem()
                awaitItem() as ProfileUiState.Chosen

                vm.reopen()
                val picking = awaitItem() as ProfileUiState.Picking
                assertEquals(true, picking.canStay)

                vm.stay()
                assertEquals(ProfileUiState.Chosen(ALICE), awaitItem())
            }
        }
}

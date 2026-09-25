package catalog.profile

import app.cash.turbine.test
import data.WatchStateRepository
import data.WatchSync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import model.ListOfSets
import model.Profile
import model.WatchSnapshot
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

private class FakeWatchStateRepository(
    initialProfiles: List<Profile> = emptyList(),
    chosenId: String? = null,
) : WatchStateRepository {
    override val profiles = MutableStateFlow(initialProfiles)
    override val chosenProfileId = MutableStateFlow(chosenId)
    override val snapshot = MutableStateFlow(WatchSnapshot.Empty)

    var reloadCalls = 0
        private set
    val created = mutableListOf<String>()
    val createdKids = mutableListOf<Boolean>()
    var reloadFailure: Exception? = null
    var chooseFailure: Exception? = null
    var createFailure: Exception? = null
    var refuseWrites = false
    var failAfterChoosing = false

    override suspend fun reload() {
        reloadCalls++
        reloadFailure?.let { throw it }
    }

    override fun invalidate() {
        profiles.value = emptyList()
        chosenProfileId.value = null
        snapshot.value = WatchSnapshot.Empty
    }

    override suspend fun chooseProfile(id: String): Boolean {
        chooseFailure?.let { throw it }
        if (refuseWrites) return false
        if (profiles.value.none { it.id == id }) return false
        chosenProfileId.value = id
        if (failAfterChoosing) error("could not read the profile snapshot")
        return true
    }

    /** As the core does: the row goes, and so does the choice when it named it. */
    override suspend fun deleteProfile(id: String): Boolean {
        if (profiles.value.none { it.id == id }) return false
        profiles.value = profiles.value.filterNot { it.id == id }
        if (chosenProfileId.value == id) chosenProfileId.value = null
        return true
    }

    override suspend fun createProfile(
        name: String,
        kids: Boolean,
    ): Profile? {
        createFailure?.let { throw it }
        if (refuseWrites) return null
        created += name
        createdKids += kids
        val made = Profile("new-${created.size}", name, kids)
        profiles.value = profiles.value + made
        return made
    }

    override suspend fun setProgress(
        setId: String,
        at: Double,
        duration: Double?,
    ) = Unit

    override suspend fun clearProgress(setId: String) = Unit

    override suspend fun setWatched(
        setId: String,
        finished: Boolean,
    ) = Unit

    override suspend fun setWatchlisted(
        setId: String,
        listed: Boolean,
    ) = Unit

    override suspend fun setKids(
        setId: String,
        marked: Boolean,
    ) = Unit

    override suspend fun createList(name: String): ListOfSets? = null

    override suspend fun renameList(
        id: String,
        name: String,
    ) = false

    override suspend fun deleteList(id: String) = false

    override suspend fun setInList(
        id: String,
        setId: String,
        included: Boolean,
    ) = false
}

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
 */
class ProfileViewModelTest {
    @Test
    fun immediateReentryReconcilesARetainedViewModelAfterReset() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository = FakeWatchStateRepository(listOf(Profile("p1", "Alice")), "p1")
            val vm = ProfileViewModel(repository, FakeWatchSync())
            vm.state.test {
                awaitItem()
                assertEquals(ProfileUiState.Chosen(Profile("p1", "Alice")), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            runCurrent()
            // Setup has cleared the repository while this Activity's ViewModel survives.
            repository.profiles.value = emptyList()
            repository.chosenProfileId.value = null
            runCurrent()
            vm.state.test {
                runCurrent()
                assertEquals(ProfileUiState.Picking(emptyList(), false), vm.state.value)
                assertEquals(2, repository.reloadCalls)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun anInitialReadFailureKeepsKnownProfilesAndExitsLoading() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val profiles = listOf(Profile("p1", "Alice"))
            val repository = FakeWatchStateRepository(profiles).apply { reloadFailure = IllegalStateException("private path") }
            val vm = ProfileViewModel(repository, FakeWatchSync())
            vm.state.test {
                awaitItem()
                assertEquals(ProfileUiState.Picking(profiles, false, "Could not load profiles. Please try again."), awaitItem())
                repository.reloadFailure = null
                vm.retry()
                runCurrent()
                assertEquals(ProfileUiState.Picking(profiles, false), vm.state.value)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aFailedAddKeepsThePickerAndDoesNotInventAProfile() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val profiles = listOf(Profile("p1", "Alice"))
            val repository = FakeWatchStateRepository(profiles).apply { createFailure = IllegalStateException("private path") }
            val vm = ProfileViewModel(repository, FakeWatchSync())
            vm.state.test {
                awaitItem()
                awaitItem()
                vm.add("Bea", kids = false)
                assertEquals(ProfileUiState.Picking(profiles, false, "Could not create the profile. Please try again."), awaitItem())
                assertEquals(emptyList(), repository.created)
                assertEquals(null, repository.chosenProfileId.value)
            }
        }

    @Test
    fun aFailedChoiceKeepsTheExistingProfileAndPicker() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val profiles = listOf(Profile("p1", "Alice"), Profile("p2", "Bea"))
            val repository = FakeWatchStateRepository(profiles, "p1").apply { chooseFailure = IllegalStateException("private path") }
            val vm = ProfileViewModel(repository, FakeWatchSync())
            vm.state.test {
                awaitItem()
                awaitItem()
                vm.reopen()
                awaitItem()
                vm.choose("p2")
                assertEquals(ProfileUiState.Picking(profiles, true, "Could not choose that profile. Please try again."), awaitItem())
                vm.stay()
                assertEquals(ProfileUiState.Chosen(profiles.first()), awaitItem())
            }
        }

    @Test
    fun aChoiceWhoseSnapshotFailedCannotBeAcceptedThroughStay() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val profiles = listOf(Profile("p1", "Alice"), Profile("p2", "Bea"))
            val repository = FakeWatchStateRepository(profiles, "p1").apply { failAfterChoosing = true }
            val vm = ProfileViewModel(repository, FakeWatchSync())
            vm.state.test {
                awaitItem()
                awaitItem()
                vm.reopen()
                awaitItem()
                vm.choose("p2")
                val failed = ProfileUiState.Picking(profiles, false, "Could not choose that profile. Please try again.")
                assertEquals(failed, awaitItem())
                vm.stay()
                runCurrent()
                expectNoEvents()
                assertEquals(failed, vm.state.value)
                repository.failAfterChoosing = false
                vm.choose("p2")
                assertEquals(ProfileUiState.Chosen(profiles.last()), awaitItem())
            }
        }

    @Test
    fun aFailedReloadKeepsTheExistingWayToStay() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val profiles = listOf(Profile("p1", "Alice"))
            val repository = FakeWatchStateRepository(profiles, "p1")
            val vm = ProfileViewModel(repository, FakeWatchSync())
            vm.state.test {
                awaitItem()
                awaitItem()
                vm.reopen()
                awaitItem()
                repository.reloadFailure = IllegalStateException("private path")
                vm.retry()
                runCurrent()
                assertEquals(ProfileUiState.Picking(profiles, true, "Could not load profiles. Please try again."), vm.state.value)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun refusedProfileWritesAreShownAsFailures() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository = FakeWatchStateRepository().apply { refuseWrites = true }
            val vm = ProfileViewModel(repository, FakeWatchSync())
            vm.state.test {
                awaitItem()
                awaitItem()
                vm.add("Bea", kids = false)
                assertEquals("Could not create the profile. Please try again.", (awaitItem() as ProfileUiState.Picking).error)
                vm.choose("p1")
                assertEquals("Could not choose that profile. Please try again.", (awaitItem() as ProfileUiState.Picking).error)
            }
        }

    @Test
    fun cancelledProfileWritesLeaveThePickerUnchanged() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository =
                FakeWatchStateRepository().apply {
                    createFailure = CancellationException("cancelled")
                    chooseFailure = CancellationException("cancelled")
                }
            val vm = ProfileViewModel(repository, FakeWatchSync())
            vm.state.test {
                awaitItem()
                val before = awaitItem()
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
            val repository =
                FakeWatchStateRepository(
                    initialProfiles = listOf(Profile("p1", "Alice")),
                    chosenId = "p1",
                )
            val vm = ProfileViewModel(repository, FakeWatchSync(settles = false))

            vm.state.test {
                assertEquals(ProfileUiState.Loading, awaitItem())
                assertEquals(ProfileUiState.Chosen(Profile("p1", "Alice")), awaitItem())
            }
        }

    @Test
    fun nobodyChosenShowsThePickerOnceTheFirstRoundSettles() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository = FakeWatchStateRepository(initialProfiles = listOf(Profile("p1", "Alice")))
            val vm = ProfileViewModel(repository, FakeWatchSync(settles = true))

            vm.state.test {
                assertEquals(ProfileUiState.Loading, awaitItem())
                val picking = awaitItem() as ProfileUiState.Picking
                assertEquals(listOf(Profile("p1", "Alice")), picking.profiles)
                assertEquals(false, picking.canStay, "nothing chosen yet, nothing to stay as")
            }
        }

    /** The 5-second cap: a round still running does not keep the picker off screen forever. */
    @Test
    fun aRoundStillRunningStopsBlockingThePickerAfterFiveSeconds() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository = FakeWatchStateRepository()
            val vm = ProfileViewModel(repository, FakeWatchSync(settles = false))

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
            val repository = FakeWatchStateRepository(initialProfiles = listOf(Profile("p1", "Alice")))
            val vm = ProfileViewModel(repository, FakeWatchSync())

            vm.state.test {
                awaitItem()
                awaitItem() as ProfileUiState.Picking

                vm.choose("p1")

                assertEquals(ProfileUiState.Chosen(Profile("p1", "Alice")), awaitItem())
            }
        }

    /** Matches the web: creating a profile lists it, and does not choose it. */
    @Test
    fun addingAProfileListsItWithoutChoosingIt() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository = FakeWatchStateRepository()
            val vm = ProfileViewModel(repository, FakeWatchSync())

            vm.state.test {
                awaitItem()
                awaitItem() as ProfileUiState.Picking

                vm.add("Bea", kids = false)

                val after = awaitItem() as ProfileUiState.Picking
                assertEquals(listOf("Bea"), after.profiles.map { it.name })
                assertEquals(listOf("Bea"), repository.created)
            }
        }

    @Test
    fun addingAKidsProfilePassesTheFlagThrough() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository = FakeWatchStateRepository(emptyList())
            val vm = ProfileViewModel(repository, FakeWatchSync())
            vm.add("Mia", kids = true)
            advanceUntilIdle()
            assertEquals(listOf(true), repository.createdKids)
            assertEquals(true, repository.profiles.value.single().kids)
        }

    /** The bar action: reopens the picker over whoever was already chosen, with a way back. */
    @Test
    fun reopenShowsThePickerWithAWayToStay() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository =
                FakeWatchStateRepository(
                    initialProfiles = listOf(Profile("p1", "Alice"), Profile("p2", "Bea")),
                    chosenId = "p1",
                )
            val vm = ProfileViewModel(repository, FakeWatchSync())

            vm.state.test {
                awaitItem()
                awaitItem() as ProfileUiState.Chosen

                vm.reopen()
                val picking = awaitItem() as ProfileUiState.Picking
                assertEquals(true, picking.canStay)

                vm.stay()
                assertEquals(ProfileUiState.Chosen(Profile("p1", "Alice")), awaitItem())
            }
        }

    @Test
    fun removingAnotherProfileKeepsTheWayToStay() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository =
                FakeWatchStateRepository(
                    initialProfiles = listOf(Profile("p1", "Alice"), Profile("p2", "Probe")),
                    chosenId = "p1",
                )
            val vm = ProfileViewModel(repository, FakeWatchSync())

            vm.state.test {
                awaitItem()
                awaitItem() as ProfileUiState.Chosen
                vm.reopen()
                awaitItem() as ProfileUiState.Picking

                vm.remove("p2")
                runCurrent()

                val picking = expectMostRecentItem() as ProfileUiState.Picking
                assertEquals(listOf(Profile("p1", "Alice")), picking.profiles)
                assertEquals(true, picking.canStay)
            }
        }

    /** Nobody is left to stay as, so the picker has to be answered. */
    @Test
    fun removingTheChosenProfileTakesAwayStayAsIAm() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository =
                FakeWatchStateRepository(
                    initialProfiles = listOf(Profile("p1", "Alice"), Profile("p2", "Bea")),
                    chosenId = "p1",
                )
            val vm = ProfileViewModel(repository, FakeWatchSync())

            vm.state.test {
                awaitItem()
                awaitItem() as ProfileUiState.Chosen
                vm.reopen()
                awaitItem() as ProfileUiState.Picking

                vm.remove("p1")
                runCurrent()

                val picking = expectMostRecentItem() as ProfileUiState.Picking
                assertEquals(listOf(Profile("p2", "Bea")), picking.profiles)
                assertEquals(false, picking.canStay)
            }
        }
}

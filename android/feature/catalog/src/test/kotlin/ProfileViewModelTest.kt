package catalog

import app.cash.turbine.test
import data.WatchStateRepository
import data.WatchSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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

    override suspend fun reload() {
        reloadCalls++
    }

    override suspend fun choose(id: String): Boolean {
        if (profiles.value.none { it.id == id }) return false
        chosenProfileId.value = id
        return true
    }

    override suspend fun create(name: String): Profile? {
        created += name
        val made = Profile("new-${created.size}", name)
        profiles.value = profiles.value + made
        return made
    }

    override suspend fun setProgress(setId: String, at: Double, duration: Double?) = Unit
    override suspend fun clearProgress(setId: String) = Unit
    override suspend fun setWatched(setId: String, finished: Boolean) = Unit
    override suspend fun setWatchlisted(setId: String, listed: Boolean) = Unit
    override suspend fun setKids(setId: String, marked: Boolean) = Unit
    override suspend fun createList(name: String): ListOfSets? = null
    override suspend fun renameList(id: String, name: String) = false
    override suspend fun deleteList(id: String) = false
    override suspend fun setInList(id: String, setId: String, included: Boolean) = false
}

/** [settles] false stands in for a round that is still in flight when the picker asks. */
private class FakeWatchSync(private val settles: Boolean = true) : WatchSync {
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

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aProfileAlreadyChosenIsShownWithoutWaitingForASyncRound() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeWatchStateRepository(
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
    fun nobodyChosenShowsThePickerOnceTheFirstRoundSettles() = runTest {
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
    fun aRoundStillRunningStopsBlockingThePickerAfterFiveSeconds() = runTest {
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
    fun choosingAKnownProfileShowsItAsChosen() = runTest {
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
    fun addingAProfileListsItWithoutChoosingIt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeWatchStateRepository()
        val vm = ProfileViewModel(repository, FakeWatchSync())

        vm.state.test {
            awaitItem()
            awaitItem() as ProfileUiState.Picking

            vm.add("Bea")

            val after = awaitItem() as ProfileUiState.Picking
            assertEquals(listOf("Bea"), after.profiles.map { it.name })
            assertEquals(listOf("Bea"), repository.created)
        }
    }

    /** The bar action: reopens the picker over whoever was already chosen, with a way back. */
    @Test
    fun reopenShowsThePickerWithAWayToStay() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeWatchStateRepository(
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
}

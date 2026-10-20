package catalog.profile

import data.DefaultHouseholdsRepository
import data.DefaultWatchStateRepository
import data.HouseholdsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import org.junit.Rule
import testing.FakeCore
import testing.FakeCoreProvider
import testing.MainDispatcherRule
import model.Households
import uniffi.mediagram_core.Profile
import kotlin.test.assertIs

/**
 * The owner's Households part over [FakeCore]: andre, the owner, runs Home
 * with Mia; Bea runs Flat, with Cy, who has no PIN; Barn is empty. This
 * device shows Home, with nobody watching unless a test says so.
 */
abstract class HouseholdsOwnerHarness {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    internal val sync = RecordingSync()
    val core =
        FakeCore().apply {
            profiles =
                listOf(
                    Profile("a", "andre", admin = true, owner = true),
                    Profile("m", "Mia", kids = true, kidsAge = 6u, parentId = "a"),
                    Profile("b", "Bea", admin = true, household = "flat"),
                    Profile("c", "Cy", household = "flat"),
                )
            roles.pins["a"] = "1234"
            roles.pins["b"] = "5678"
            homes.names["flat"] = "Flat"
            homes.names["barn"] = "Barn"
        }
    val provider = FakeCoreProvider(core)
    val repository = DefaultWatchStateRepository(provider, Dispatchers.Unconfined)
    lateinit var households: DefaultHouseholdsRepository

    /** What [HouseholdsOwnerViewModel.joined] said, in order. */
    val said = mutableListOf<Boolean>()

    suspend fun TestScope.owning(): HouseholdsOwnerViewModel {
        repository.reload()
        households = DefaultHouseholdsRepository(provider, repository, backgroundScope, Dispatchers.Unconfined)
        return HouseholdsOwnerViewModel(repository, households, sync).also { vm -> backgroundScope.launch { vm.joined.collect { said += it } } }
    }

    /** The part opened by the owner's PIN. */
    suspend fun TestScope.opened(): HouseholdsOwnerViewModel =
        owning().apply {
            open()
            enterPin("1234")
        }

    fun HouseholdsOwnerViewModel.part() = assertIs<HouseholdsOwnerUiState.Open>(state.value)

    fun household(id: String) = households.view.value.list.find { it.id == id }

    fun profile(id: String) = households.view.value.everyone.single { it.id == id }
}

/** Households never read: Manage draws this device's household alone, as it did before there were others. */
internal object HouseholdsUnread : HouseholdsRepository {
    override val view: StateFlow<Households> = MutableStateFlow(Households())

    override suspend fun reload() = Unit
}

package catalog.profile

import data.DefaultHouseholdsRepository
import data.DefaultWatchStateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import model.HOME_HOUSEHOLD
import model.HouseholdWords
import org.junit.Rule
import testing.FakeCore
import testing.FakeCoreProvider
import testing.MainDispatcherRule
import uniffi.mediagram_core.Profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Join a household over [FakeCore]: andre, the owner, runs Home; Bea runs
 * Flat, with Cy; Dan runs Barn and has no PIN, so only the owner's opens it.
 * The device has joined none, and chosen nobody, unless a test says so.
 */
class JoinHouseholdViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val core =
        FakeCore().apply {
            profiles =
                listOf(
                    Profile("a", "andre", admin = true, owner = true),
                    Profile("b", "Bea", admin = true, household = "flat"),
                    Profile("c", "Cy", household = "flat"),
                    Profile("d", "Dan", admin = true, household = "barn"),
                )
            roles.pins["a"] = "1234"
            roles.pins["b"] = "5678"
            homes.names["flat"] = "Flat"
            homes.names["barn"] = "Barn"
            homes.joined = null
        }
    private val provider = FakeCoreProvider(core)
    private val repository = DefaultWatchStateRepository(provider, Dispatchers.Unconfined)
    private lateinit var households: DefaultHouseholdsRepository

    /** What [JoinHouseholdViewModel.joined] said, in order. */
    private val said = mutableListOf<Boolean>()

    /** [unread]: the households cannot be read, though the profiles were. */
    private suspend fun TestScope.joining(unread: Boolean = false): JoinHouseholdViewModel {
        repository.reload()
        if (unread) provider.beforeCore = { error("keystore unavailable") }
        households = DefaultHouseholdsRepository(provider, repository, backgroundScope, Dispatchers.Unconfined)
        return JoinHouseholdViewModel(repository, households).also { vm -> backgroundScope.launch { vm.joined.collect { said += it } } }
    }

    private fun JoinHouseholdViewModel.offered() = state.value.households.map { it.name }

    @Test
    fun offersEveryHouseholdToADeviceThatHasNone() =
        runTest(UnconfinedTestDispatcher()) {
            val vm = joining()
            assertEquals(listOf("Barn", "Flat", "Home"), vm.offered())
            // Nobody is here, and a round was heard: Join a household, never "Create the first profile".
            assertTrue(vm.state.value.needsJoin)
            assertEquals(emptyList(), repository.profiles.value)
        }

    @Test
    fun aHouseholdNobodyCanOpenIsNotOffered() =
        runTest(UnconfinedTestDispatcher()) {
            core.roles.pins.remove("a")
            assertEquals(listOf("Flat"), joining().offered())
        }

    @Test
    fun nothingIsAskedBeforeTheHouseholdsAreRead() =
        runTest(UnconfinedTestDispatcher()) {
            val vm = joining(unread = true)
            assertTrue(repository.syncedOnce.value)
            assertEquals(JoinUiState(), vm.state.value)
        }

    @Test
    fun switchingLeavesOutThisDevicesHousehold() =
        runTest(UnconfinedTestDispatcher()) {
            core.homes.joined = HOME_HOUSEHOLD
            core.chosen = "a"
            val vm = joining()
            assertEquals(listOf("Barn", "Flat"), vm.offered())
            assertEquals("Home", vm.state.value.here?.name)
            assertTrue(vm.state.value.canSwitch)
            assertFalse(vm.state.value.needsJoin)
            assertFalse(vm.state.value.joinsElsewhere)
        }

    /** A household made on another device changes nobody here, so the list is read again as it is shown. */
    @Test
    fun aHouseholdMadeElsewhereIsOfferedOnceReadAgain() =
        runTest(UnconfinedTestDispatcher()) {
            val vm = joining()
            core.homes.names["loft"] = "Loft"
            assertEquals(listOf("Barn", "Flat", "Home"), vm.offered())
            vm.reread()
            assertEquals(listOf("Barn", "Flat", "Home", "Loft"), vm.offered())
        }

    @Test
    fun anEmptyHouseholdOffersTheOthers() =
        runTest(UnconfinedTestDispatcher()) {
            core.homes.joined = "barn"
            core.profiles = core.profiles.filterNot { it.id == "d" }
            val vm = joining()
            // The core makes no first profile while a grown-up is anywhere on the install.
            assertTrue(vm.state.value.joinsElsewhere)
            assertFalse(vm.state.value.needsJoin)
            assertEquals(listOf("Flat", "Home"), vm.offered())
        }

    @Test
    fun theAdminsPinJoins() =
        runTest(UnconfinedTestDispatcher()) {
            val vm = joining()
            vm.pick("flat")
            assertEquals(PinPrompt("Bea’s PIN, or the owner’s", newPin = false), vm.pin.value)
            vm.enterPin("5678")
            assertNull(vm.pin.value)
            assertEquals("flat", households.view.value.joined)
            assertEquals(listOf("Bea", "Cy"), repository.profiles.value.map { it.name })
            assertFalse(vm.state.value.needsJoin)
            assertEquals(listOf(false), said)
        }

    @Test
    fun theOwnersPinJoins() =
        runTest(UnconfinedTestDispatcher()) {
            val vm = joining()
            vm.pick("home")
            assertEquals(PinPrompt(HouseholdWords.OWNERS_PIN, newPin = false), vm.pin.value)
            vm.cancelPin()
            vm.pick("barn")
            assertEquals("Dan’s PIN, or the owner’s", vm.pin.value?.title)
            vm.enterPin("1234")
            assertEquals("barn", households.view.value.joined)
        }

    @Test
    fun aWrongPinStaysOpenWithTheReason() =
        runTest(UnconfinedTestDispatcher()) {
            val vm = joining()
            vm.pick("flat")
            vm.enterPin("0000")
            assertEquals("Wrong PIN.", vm.pin.value?.error)
            assertNull(households.view.value.joined)
            assertTrue(vm.state.value.needsJoin)
            assertEquals(emptyList(), said)
        }

    /** The core joined, but the device shows its new household only once both lists are read again. */
    @Test
    fun aFailedReadAsksAgain() =
        runTest(UnconfinedTestDispatcher()) {
            val vm = joining()
            vm.pick("flat")
            core.profilesFailure = IllegalStateException("store busy")
            vm.enterPin("5678")
            assertEquals(DID_NOT_GO_THROUGH, vm.pin.value?.error)
            assertNull(households.view.value.joined)
            assertTrue(vm.state.value.needsJoin)
            core.profilesFailure = null
            vm.enterPin("5678")
            assertEquals("flat", households.view.value.joined)
            assertEquals(listOf(false), said)
        }

    @Test
    fun saysWhenTheChosenProfileWasLeftBehind() =
        runTest(UnconfinedTestDispatcher()) {
            // Bea was chosen here before; the device shows her again once it is back in Flat.
            core.chosen = "b"
            val vm = joining()
            vm.pick("flat")
            vm.enterPin("5678")
            assertEquals("b", repository.chosenProfileId.value)
            vm.pick("home")
            vm.enterPin("1234")
            assertNull(repository.chosenProfileId.value)
            assertEquals(listOf(true, false), said)
        }
}

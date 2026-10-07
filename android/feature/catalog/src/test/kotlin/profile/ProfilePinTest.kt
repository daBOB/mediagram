package catalog.profile

import data.DefaultWatchStateRepository
import data.WatchSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import testing.FakeCore
import testing.FakeCoreProvider
import testing.MainDispatcherRule
import uniffi.mediagram_core.Profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Counts [soon]: a change made here should reach the other devices without waiting five minutes. */
internal class RecordingSync : WatchSync {
    var soonCalls = 0
        private set

    override fun onForeground() = Unit

    override fun onBackground() = Unit

    override fun soon() {
        soonCalls++
    }

    override suspend fun awaitFirstRound() = Unit
}

/** The picker over [FakeCore]'s own rules — the answers the tablet's core gives, not a hand fake's. */
class ProfilePinTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val sync = RecordingSync()

    private fun picker(profiles: List<Profile> = household()): Pair<FakeCore, ProfileViewModel> {
        val core =
            FakeCore().apply {
                this.profiles = profiles
                if (profiles.any { it.id == "a" }) roles.pins["a"] = "1234"
            }
        return core to ProfileViewModel(DefaultWatchStateRepository(FakeCoreProvider(core), Dispatchers.Unconfined), sync)
    }

    private fun household(admin: Boolean = true) =
        listOf(Profile("a", "andre", admin = admin), Profile("t", "test"), Profile("k", "TV kids", kids = true))

    /** [ProfileViewModel.state] is shared `WhileSubscribed`: something has to be watching it. */
    private fun TestScope.watch(vm: ProfileViewModel) = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

    private fun ProfileViewModel.picking() = state.value as ProfileUiState.Picking

    private fun ProfileViewModel.chosenId() = (state.value as? ProfileUiState.Chosen)?.profile?.id

    @Test
    fun aKidOpensWithoutAPin() =
        runTest {
            val (_, vm) = picker()
            watch(vm)
            vm.pick("k")
            assertEquals("k", vm.chosenId())
            assertNull(vm.pin.value)
        }

    @Test
    fun aGrownUpOpensOnlyOnItsOwnPin() =
        runTest {
            val (_, vm) = picker()
            watch(vm)
            vm.pick("a")
            assertEquals(PinPrompt("andre’s PIN", newPin = false), vm.pin.value)
            vm.enterPin("0000")
            assertEquals("Wrong PIN.", vm.pin.value?.error)
            assertTrue(vm.state.value is ProfileUiState.Picking)
            vm.enterPin("1234")
            assertEquals("a", vm.chosenId())
            assertNull(vm.pin.value)
            assertEquals(0, sync.soonCalls, "an unlock changes nothing to share")
        }

    @Test
    fun aGrownUpFromBeforePinsChoosesOneTwiceThenOpens() =
        runTest {
            val (core, vm) = picker()
            watch(vm)
            vm.pick("t")
            assertEquals(PinPrompt("Choose a PIN for test", newPin = true), vm.pin.value)
            vm.enterPin("4321")
            vm.enterPin("4321")
            assertEquals("t", vm.chosenId())
            assertEquals("4321", core.roles.pins["t"])
            assertEquals(1, sync.soonCalls)
        }

    @Test
    fun fiveWrongPinsMakeEvenTheRightOneWaitAMinute() =
        runTest {
            val (core, vm) = picker()
            watch(vm)
            vm.pick("a")
            repeat(5) { vm.enterPin("0000") }
            vm.enterPin("1234")
            assertEquals("Too many wrong PINs. Try again in 60 s.", vm.pin.value?.error)
            core.roles.nowMs += 60_000
            vm.enterPin("1234")
            assertEquals("a", vm.chosenId())
        }

    @Test
    fun aHouseholdWithNoAdminIsAskedAndTheClaimSticks() =
        runTest {
            val (_, vm) = picker(household(admin = false))
            watch(vm)
            assertEquals(true to false, vm.picking().needsAdmin to vm.picking().needsFirstProfile)
            assertEquals(listOf("a", "t"), vm.picking().grownUps.map { it.id })
            vm.claim("t")
            assertEquals(PinPrompt("Choose a PIN for test", newPin = true), vm.pin.value)
            vm.enterPin("4321")
            vm.enterPin("4321")
            assertEquals(false, vm.picking().needsAdmin)
            assertEquals(listOf("t"), vm.picking().profiles.filter { it.admin }.map { it.id })
            assertEquals(1, sync.soonCalls)
        }

    @Test
    fun aDeviceWithNoGrownUpCreatesTheFirstWhoRunsTheHousehold() =
        runTest {
            val (_, vm) = picker(listOf(Profile("k", "TV kids", kids = true)))
            watch(vm)
            assertEquals(true to false, vm.picking().needsFirstProfile to vm.picking().needsAdmin)
            vm.createFirst("  Ann ")
            assertEquals(PinPrompt("A PIN for Ann", newPin = true), vm.pin.value)
            vm.enterPin("2468")
            vm.enterPin("2468")
            val ann = vm.picking().profiles.single { it.name == "Ann" }
            assertEquals(true to true, ann.admin to ann.hasPin)
            assertEquals(false, vm.picking().needsFirstProfile)
            assertEquals(1, sync.soonCalls)
        }

    /** A refused new PIN ends its prompt; why is said above the tiles, over who is here now. */
    @Test
    fun aRefusedFirstProfileSaysWhyAboveTheTiles() =
        runTest {
            val (_, vm) = picker(listOf(Profile("k", "TV kids", kids = true)))
            watch(vm)
            vm.createFirst("tv KIDS")
            vm.enterPin("2468")
            vm.enterPin("2468")
            assertNull(vm.pin.value)
            assertEquals("A profile with that name already exists.", vm.picking().notice)

            vm.createFirst("Ann")
            assertNull(vm.picking().notice, "trying something else clears it, as on the web")
        }

    @Test
    fun aKidCannotClaimAndCancellingChangesNothing() =
        runTest {
            val (_, vm) = picker(household(admin = false))
            watch(vm)
            vm.claim("k")
            assertNull(vm.pin.value)
            vm.pick("a")
            vm.cancelPin()
            assertNull(vm.pin.value)
            assertTrue(vm.state.value is ProfileUiState.Picking)
        }

    /** A prompt left open must not answer for a picker shown again. */
    @Test
    fun reopeningThePickerCancelsAnOpenPrompt() =
        runTest {
            val (_, vm) = picker()
            watch(vm)
            vm.pick("a")
            vm.reopen()
            assertNull(vm.pin.value)
        }
}

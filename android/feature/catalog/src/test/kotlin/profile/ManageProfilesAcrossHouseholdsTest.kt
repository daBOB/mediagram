package catalog.profile

import data.DefaultHouseholdsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Manage profiles over [HouseholdsOwnerHarness]'s install: the owner's reaches every household, anyone else's only this device's. */
class ManageProfilesAcrossHouseholdsTest : HouseholdsOwnerHarness() {
    private suspend fun TestScope.managing(
        id: String,
        pin: String,
    ): ManageProfilesViewModel {
        repository.reload()
        households = DefaultHouseholdsRepository(provider, repository, backgroundScope, Dispatchers.Unconfined)
        return ManageProfilesViewModel(repository, households, sync).apply {
            open()
            actAs(id)
            enterPin(pin)
        }
    }

    private fun ManageProfilesViewModel.panel() = assertIs<ManageUiState.Managing>(state.value)

    @Test
    fun theOwnerManagesEveryHouseholdWithSomeoneInIt() =
        runTest(UnconfinedTestDispatcher()) {
            val panel = managing("a", "1234").panel()
            assertEquals(listOf("Bea", "Cy"), panel.grownUps.map { it.name })
            assertEquals(listOf("Mia"), panel.kids.map { it.name })
            // Barn has nobody in it, so it has no part.
            assertEquals(listOf("Home", "Flat"), panel.parts().map { it.name })
        }

    /** A change in another household leaves this device's profiles as they were, so the households are read again for it. */
    @Test
    fun theOwnersChangesInAnotherHouseholdAreReadBack() =
        runTest(UnconfinedTestDispatcher()) {
            val vm = managing("a", "1234")
            vm.setHiddenSections("b", listOf("anime"))
            assertEquals(listOf("anime"), vm.panel().grownUps.first().hiddenSections)
            vm.remove("c")
            assertEquals(listOf("Bea"), vm.panel().grownUps.map { it.name })
            // Both reach the other devices now, as any change made here does.
            assertEquals(2, sync.soonCalls)
        }

    @Test
    fun theOwnerResetsThePinOfAnotherHouseholdsAdmin() =
        runTest(UnconfinedTestDispatcher()) {
            val vm = managing("a", "1234")
            vm.changePin("b")
            assertEquals(PinPrompt(resetPinTitleFor("Bea"), newPin = true), vm.pin.value)
            vm.enterPin("2468")
            vm.enterPin("2468")
            assertEquals("2468", core.roles.pins["b"])
            assertNull(vm.panel().notice)
        }

    @Test
    fun anyoneElseManagesOnlyThisDevicesHousehold() =
        runTest(UnconfinedTestDispatcher()) {
            core.homes.joined = "flat"
            val panel = managing("b", "5678").panel()
            assertEquals(listOf("Cy"), panel.grownUps.map { it.name })
            assertEquals(listOf(null), panel.parts().map { it.name })
        }
}

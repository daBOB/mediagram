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

/** One owner PIN for Manage and Households, over [HouseholdsOwnerHarness]'s install. */
class OwnerPinSharedTest : HouseholdsOwnerHarness() {
    private suspend fun TestScope.manage(): ManageProfilesViewModel {
        repository.reload()
        households = DefaultHouseholdsRepository(provider, repository, backgroundScope, Dispatchers.Unconfined)
        return ManageProfilesViewModel(repository, households, sync, ownerPin)
    }

    @Test
    fun ownersPinGivenToHouseholdsIsNotAskedAgainByManage() =
        runTest(UnconfinedTestDispatcher()) {
            val (manage, owners) = watchedByTheOwner()
            owners.rename("barn", "Shed")
            owners.enterPin("1234")
            manage.setKidsAge("m", 12)
            assertNull(manage.pin.value)
            assertEquals(12, profile("m").kidsAge)
        }

    @Test
    fun ownersPinGivenToManageIsNotAskedAgainByHouseholds() =
        runTest(UnconfinedTestDispatcher()) {
            manage().apply { open(); actAs("a"); enterPin("1234") }
            val vm = owning()
            assertIs<HouseholdsOwnerUiState.Open>(vm.state.value)
            vm.rename("barn", "Shed")
            assertNull(vm.pin.value)
            assertEquals("Shed", household("barn")?.name)
        }

    @Test
    fun closingThePageDropsTheOwnersPin() =
        runTest(UnconfinedTestDispatcher()) {
            val households = opened()
            val manage = manage()
            households.close()
            assertNull(ownerPin.held)
            ownerPin.held = "1234"
            manage.close()
            assertNull(ownerPin.held)
        }

    @Test
    fun aWrongPinDropsItForBoth() =
        runTest(UnconfinedTestDispatcher()) {
            val manage = manage().apply { open(); actAs("a"); enterPin("1234") }
            core.roles.pins["a"] = "9999"
            manage.remove("c")
            assertNull(ownerPin.held)
            // The owner's view still shows: its next household change asks the PIN again.
            val vm = owning()
            vm.rename("barn", "Shed")
            assertEquals(PinPrompt(model.HouseholdWords.OWNERS_PIN, newPin = false), vm.pin.value)
        }

    @Test
    fun theOwnersNewPinReplacesTheSharedOne() =
        runTest(UnconfinedTestDispatcher()) {
            val manage = manage().apply { open(); actAs("a"); enterPin("1234") }
            manage.changePin("a")
            manage.enterPin("2468")
            manage.enterPin("2468")
            assertEquals("2468", ownerPin.held)
            val vm = owning()
            vm.rename("barn", "Shed")
            assertNull(vm.pin.value)
            assertEquals("Shed", household("barn")?.name)
        }
}

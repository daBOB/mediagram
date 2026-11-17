package catalog.profile

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import model.HouseholdWords
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * The owner's one view of the Profiles page — the web's `profile-manage-owner.test.ts` over
 * [HouseholdsOwnerHarness]'s install: every household with its actions, its people, and adding
 * right there, behind one owner's PIN per visit — from Settings, the picker, and the Owner button.
 */
class OwnerViewTest : HouseholdsOwnerHarness() {
    private val ownersPin = PinPrompt(HouseholdWords.OWNERS_PIN, newPin = false)

    /** Manage and the owner's Households part on one page, wired as the page wires them: the Owner button's PIN opens the owner's view. */
    private suspend fun TestScope.page(): Pair<ManageProfilesViewModel, HouseholdsOwnerViewModel> {
        val owners = owning()
        val manage = ManageProfilesViewModel(repository, households, sync, ownerPin)
        backgroundScope.launch { owners.ownerView.collect { manage.asOwner() } }
        return manage to owners
    }

    /** The page watched by the owner: their view at once. */
    private suspend fun TestScope.watchedByTheOwner() = page().also { (manage) -> manage.open(profile("a")) }

    private fun ManageProfilesViewModel.view() = assertIs<ManageUiState.Managing>(state.value)

    private fun ManageProfilesViewModel.part(id: String) = view().parts().single { it.id == id }

    private fun newPin(name: String) = PinPrompt(newPinTitleFor(name), newPin = true)

    @Test
    fun watchingAsTheOwnerOpensManagingWithoutWhoAreYou() =
        runTest(UnconfinedTestDispatcher()) {
            val (manage) = watchedByTheOwner()
            assertEquals("andre", manage.view().actor.name)
            // This device's household first, then the others in the live order: the empty Barn too.
            assertEquals(listOf("Home", "Barn", "Flat"), manage.view().parts().map { it.name })
            // Seeing it asks nothing: the PIN waits for the first change.
            assertNull(manage.pin.value)
            assertNull(ownerPin.held)
        }

    @Test
    fun theFirstChangeAsksTheOwnersPinOnce() =
        runTest(UnconfinedTestDispatcher()) {
            val (manage, owners) = watchedByTheOwner()
            manage.setKidsAge("m", 12)
            assertEquals(ownersPin, manage.pin.value)
            assertEquals(6, profile("m").kidsAge)
            manage.enterPin("1234")
            assertEquals(12, profile("m").kidsAge)
            manage.setDailyLimit("m", 30)
            assertNull(manage.pin.value)
            assertEquals(30, profile("m").dailyLimit)
            // Held for the households' actions on the same page too.
            owners.rename("barn", "Shed")
            assertNull(owners.pin.value)
            assertEquals("Shed", household("barn")?.name)
        }

    @Test
    fun addGrownUpInAnotherHouseholdSendsThatHousehold() =
        runTest(UnconfinedTestDispatcher()) {
            val (manage) = watchedByTheOwner()
            manage.addGrownUp("Opa", "flat")
            // The owner's PIN first, the first change of the visit; then the new grown-up's, twice.
            assertEquals(ownersPin, manage.pin.value)
            manage.enterPin("1234")
            assertEquals(newPin("Opa"), manage.pin.value)
            manage.enterPin("2468")
            manage.enterPin("2468")
            assertNull(manage.pin.value)
            assertEquals(listOf("Bea", "Cy", "Opa"), manage.part("flat").grownUps.map { it.name })
        }

    @Test
    fun addKidElsewhereBelongsToItsAdmin() =
        runTest(UnconfinedTestDispatcher()) {
            val (manage) = watchedByTheOwner()
            manage.addKid("Ben", 6, null, manage.part("flat").kidParents.first().id)
            manage.enterPin("1234")
            assertEquals("flat" to "b", households.view.value.everyone.single { it.name == "Ben" }.let { it.household to it.parentId })
            assertEquals(listOf("Ben"), manage.part("flat").kids.map { it.name })
        }

    @Test
    fun aHouseholdWithTwoGrownUpsListsBothAsKidParents() =
        runTest(UnconfinedTestDispatcher()) {
            // Cy listed before Bea, who runs the flat.
            core.profiles = core.profiles.sortedBy { it.id != "c" }
            val (manage) = watchedByTheOwner()
            assertEquals(listOf("Bea", "Cy"), manage.part("flat").kidParents.map { it.name })
            // At home a new kid is the owner's, as it always was.
            assertEquals(listOf("andre"), manage.part("home").kidParents.map { it.name })
        }

    @Test
    fun anEmptyHouseholdAddsNoKid() =
        runTest(UnconfinedTestDispatcher()) {
            val barn = watchedByTheOwner().first.part("barn")
            assertEquals(true to false, barn.addsGrownUp to barn.addsKid)
            assertEquals(emptyList<model.Profile>(), barn.kidParents)
            assertEquals(true, barn.household?.canDelete)
        }

    @Test
    fun ownerButtonOpensTheWholeOwnerView() =
        runTest(UnconfinedTestDispatcher()) {
            core.homes.joined = "flat"
            val (manage, owners) = page()
            manage.open()
            // The owner is not one of this household's to choose.
            assertEquals(listOf("Bea", "Cy"), assertIs<ManageUiState.ChoosingActor>(manage.state.value).grownUps.map { it.name })
            owners.open()
            owners.enterPin("1234")
            assertEquals("andre", manage.view().actor.name)
            assertEquals(listOf("Flat", "Barn", "Home"), manage.view().parts().map { it.name })
            assertEquals(listOf("Mia"), manage.part("home").kids.map { it.name })
            // The owner's PIN is held: adding asks only the new grown-up's.
            manage.addGrownUp("Opa", "barn")
            assertEquals(newPin("Opa"), manage.pin.value)
        }

    @Test
    fun partsCarryTheirHouseholdsActionsForTheOwnerOnly() =
        runTest(UnconfinedTestDispatcher()) {
            val (manage, owners) = watchedByTheOwner()
            val rows: Map<String?, HouseholdRow?> = owners.part().rows.associateBy { it.household.id }
            assertEquals(rows, manage.view().parts().associate { it.id to it.household })
            // Anyone else's Manage carries none, and adds a kid as its own.
            manage.close()
            core.homes.joined = "flat"
            repository.reload()
            households.reload()
            manage.open()
            manage.actAs("b")
            manage.enterPin("5678")
            val part = manage.view().parts().single()
            assertNull(part.household)
            assertEquals(emptyList<model.Profile>(), part.kidParents)
        }

    @Test
    fun aHouseholdAddedElsewhereAppearsWithoutAskingThePinAgain() =
        runTest(UnconfinedTestDispatcher()) {
            val (manage) = watchedByTheOwner()
            manage.setKidsAge("m", 12)
            manage.enterPin("1234")
            // Another device creates the attic.
            core.homes.names["attic"] = "Attic"
            households.reload()
            assertEquals(listOf("Home", "Attic", "Barn", "Flat"), manage.view().parts().map { it.name })
            manage.addGrownUp("Opa", "attic")
            assertEquals(newPin("Opa"), manage.pin.value)
            manage.enterPin("2468")
            manage.enterPin("2468")
            assertEquals(listOf("Opa"), manage.part("attic").grownUps.map { it.name })
        }

    @Test
    fun choosingTheOwnerInWhoAreYouOpensTheOwnerView() =
        runTest(UnconfinedTestDispatcher()) {
            val (manage, owners) = page()
            manage.open()
            manage.actAs("a")
            manage.enterPin("1234")
            assertEquals(listOf("Home", "Barn", "Flat"), manage.view().parts().map { it.name })
            // The households' actions are the view's too, under the PIN given there.
            owners.rename("barn", "Shed")
            assertNull(owners.pin.value)
            assertEquals("Shed", household("barn")?.name)
        }

    @Test
    fun aWrongPinKeepsTheOwnerViewAndAsksAgainAtTheNextChange() =
        runTest(UnconfinedTestDispatcher()) {
            val (manage, owners) = watchedByTheOwner()
            manage.setKidsAge("m", 12)
            manage.enterPin("1234")
            // Changed on another device since it was given here.
            core.roles.pins["a"] = "9999"
            manage.setKidsAge("m", 6)
            assertEquals("Wrong PIN.", manage.view().notice)
            assertNull(ownerPin.held)
            owners.part()
            manage.setKidsAge("m", 6)
            assertEquals(ownersPin, manage.pin.value)
            manage.enterPin("9999")
            assertEquals(6, profile("m").kidsAge)
        }
}

package catalog.profile

import data.DefaultHouseholdsRepository
import data.DefaultWatchStateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import model.Household
import model.HouseholdWords
import org.junit.Rule
import testing.FakeCore
import testing.FakeCoreProvider
import testing.MainDispatcherRule
import uniffi.mediagram_core.Profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * The owner's Households part over [FakeCore]: andre, the owner, runs Home
 * with Mia; Bea runs Flat, with Cy, who has no PIN; Barn is empty. This
 * device shows Home, as andre.
 */
class HouseholdsOwnerViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val sync = RecordingSync()
    private val core =
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
            chosen = "a"
        }
    private val provider = FakeCoreProvider(core)
    private val repository = DefaultWatchStateRepository(provider, Dispatchers.Unconfined)
    private lateinit var households: DefaultHouseholdsRepository

    /** What [HouseholdsOwnerViewModel.joined] said, in order. */
    private val said = mutableListOf<Boolean>()

    private suspend fun TestScope.owning(): HouseholdsOwnerViewModel {
        repository.reload()
        households = DefaultHouseholdsRepository(provider, repository, backgroundScope, Dispatchers.Unconfined)
        return HouseholdsOwnerViewModel(repository, households, sync).also { vm -> backgroundScope.launch { vm.joined.collect { said += it } } }
    }

    /** The part opened by the owner's PIN. */
    private suspend fun TestScope.opened(): HouseholdsOwnerViewModel =
        owning().apply {
            open()
            enterPin("1234")
        }

    private fun HouseholdsOwnerViewModel.part() = assertIs<HouseholdsOwnerUiState.Open>(state.value)

    private fun household(id: String) = households.view.value.list.find { it.id == id }

    private fun profile(id: String) = households.view.value.everyone.single { it.id == id }

    @Test
    fun theOwnersPinOpensThePartOncePerVisit() =
        runTest(UnconfinedTestDispatcher()) {
            val vm = owning()
            assertEquals(HouseholdsOwnerUiState.Closed(canOpen = true), vm.state.value)
            vm.open()
            assertEquals(PinPrompt(HouseholdWords.OWNERS_PIN, newPin = false), vm.pin.value)
            vm.enterPin("0000")
            assertEquals("Wrong PIN.", vm.pin.value?.error)
            assertIs<HouseholdsOwnerUiState.Closed>(vm.state.value)
            vm.enterPin("1234")
            vm.part()
            vm.rename("barn", "Shed")
            // Held: the change asks nothing more.
            assertNull(vm.pin.value)
            assertEquals("Shed", household("barn")?.name)
        }

    @Test
    fun rowsOfferWhatTheRuleAllows() =
        runTest(UnconfinedTestDispatcher()) {
            val vm = opened()
            val cy = profile("c")
            assertEquals(
                listOf(
                    HouseholdRow(Household("barn", "Barn", null), "No admin · 0 profiles", isHere = false, canRename = true, canUseHere = true, canDelete = true, adminChoices = emptyList(), moveChoices = listOf(cy)),
                    // Bea runs Flat while Cy is there, so she does not move; Cy is already in.
                    HouseholdRow(Household("flat", "Flat", "b"), "Admin Bea · 2 profiles", isHere = false, canRename = true, canUseHere = true, canDelete = false, adminChoices = listOf(cy), moveChoices = emptyList()),
                    // The owner never moves; Home is never deleted.
                    HouseholdRow(Household("home", "Home", "a"), "Admin andre · 2 profiles", isHere = true, canRename = true, canUseHere = false, canDelete = false, adminChoices = emptyList(), moveChoices = listOf(cy)),
                ),
                vm.part().rows,
            )
            assertEquals(listOf(cy), vm.part().createChoices)
        }

    @Test
    fun changeAdminIsNotOfferedForTheOwnersHousehold() =
        runTest(UnconfinedTestDispatcher()) {
            core.profiles += Profile("e", "Eve")
            core.roles.pins["e"] = "4321"
            val vm = opened()
            assertEquals(false, vm.part().rows.single { it.isHere }.canChangeAdmin)
            assertEquals(true, vm.part().rows.single { it.household.id == "flat" }.canChangeAdmin)
            // Not offered, and refused all the same if asked.
            vm.changeAdmin("home", "e")
            assertEquals("That is not allowed.", vm.part().notice)
            assertEquals("a", household("home")?.adminId)
        }

    @Test
    fun eachActionSendsTheHeldPin() =
        runTest(UnconfinedTestDispatcher()) {
            val vm = opened()
            // A rename changes nobody here, so only the part's own re-read shows it.
            vm.rename("barn", "Shed")
            assertEquals("Shed", household("barn")?.name)
            vm.delete("barn")
            assertNull(household("barn"))
            // Cy has no PIN: the new admin's first one is typed twice before anything is sent.
            vm.changeAdmin("flat", "c")
            assertEquals(PinPrompt("A PIN for Cy", newPin = true), vm.pin.value)
            vm.enterPin("2468")
            vm.enterPin("2468")
            assertEquals("c", household("flat")?.adminId)
            vm.moveHere("home", "b")
            assertEquals("home", profile("b").household)
            vm.create("Loft", adminId = null, adminName = "Dee")
            vm.enterPin("1357")
            vm.enterPin("1357")
            assertEquals("Dee", profile(households.view.value.list.single { it.name == "Loft" }.adminId!!).name)
            assertNull(vm.part().notice)
            assertEquals(5, sync.soonCalls)
            // Joining changes nothing synced, and leaves andre, who is watching, behind.
            vm.useHere("flat")
            assertEquals("flat", households.view.value.joined)
            assertEquals(5, sync.soonCalls)
            assertEquals(listOf(false), said)
        }

    @Test
    fun aTakenHouseholdNameSaysSoInItsOwnWords() =
        runTest(UnconfinedTestDispatcher()) {
            val vm = opened()
            vm.rename("barn", " flat ")
            assertEquals(HouseholdWords.HOUSEHOLD_NAME_TAKEN, vm.part().notice)
            // Both names can be taken; the household's is said first, as the core checks it first.
            vm.create("FLAT", adminId = null, adminName = "Bea")
            vm.enterPin("1357")
            vm.enterPin("1357")
            assertEquals(HouseholdWords.HOUSEHOLD_NAME_TAKEN, vm.part().notice)
            vm.create("Loft", adminId = null, adminName = "bea")
            vm.enterPin("1357")
            vm.enterPin("1357")
            assertEquals(HouseholdWords.PROFILE_NAME_TAKEN, vm.part().notice)
            assertEquals(listOf("Barn", "Flat", "Home"), households.view.value.list.map { it.name })
        }

    @Test
    fun closeDropsThePin() =
        runTest(UnconfinedTestDispatcher()) {
            val vm = opened()
            vm.close()
            assertEquals(HouseholdsOwnerUiState.Closed(canOpen = true), vm.state.value)
            // With the owner watching the part shows at once, and the PIN is asked at the first change.
            vm.openAsOwner()
            vm.part()
            vm.rename("barn", "Shed")
            assertEquals(PinPrompt(HouseholdWords.OWNERS_PIN, newPin = false), vm.pin.value)
            assertEquals("Barn", household("barn")?.name)
            vm.enterPin("1234")
            assertEquals("Shed", household("barn")?.name)
        }
}

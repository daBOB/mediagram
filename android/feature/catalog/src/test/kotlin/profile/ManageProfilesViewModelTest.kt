package catalog.profile

import catalog.MainDispatcherRule
import data.DefaultWatchStateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import testing.CatalogCoreProvider
import testing.FakeCore
import uniffi.mediagram_core.Profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Manage over [FakeCore]'s rules: andre (admin) with Mia; Bea with Tom; TV kids from before parents. */
class ManageProfilesViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val sync = RecordingSync()
    private val core =
        FakeCore().apply {
            profiles =
                listOf(
                    Profile("a", "andre", admin = true),
                    Profile("b", "Bea"),
                    Profile("m", "Mia", kids = true, kidsAge = 6u, parentId = "a"),
                    Profile("t", "Tom", kids = true, kidsAge = 12u, parentId = "b"),
                    Profile("o", "TV kids", kids = true),
                )
            roles.pins["a"] = "1234"
            roles.pins["b"] = "5678"
        }
    private val repository = DefaultWatchStateRepository(CatalogCoreProvider(core), Dispatchers.Unconfined)

    // Lazy: the view model reaches Dispatchers.Main as it is built, and the
    // rule installs the test one only once the test itself starts.
    private val vm by lazy { ManageProfilesViewModel(repository, sync) }

    private suspend fun panelAs(
        id: String,
        pin: String,
    ): ManageUiState.Managing {
        repository.reload()
        vm.open()
        vm.actAs(id)
        vm.enterPin(pin)
        return assertIs<ManageUiState.Managing>(vm.state.value)
    }

    private fun managing() = assertIs<ManageUiState.Managing>(vm.state.value)

    @Test
    fun onlyGrownUpsAreAskedWhoTheyAre() =
        runTest {
            repository.reload()
            assertIs<ManageUiState.Closed>(vm.state.value)
            vm.open()
            assertEquals(listOf("a", "b"), assertIs<ManageUiState.ChoosingActor>(vm.state.value).grownUps.map { it.id })
            vm.actAs("m")
            assertNull(vm.pin.value)
        }

    @Test
    fun aWrongPinDoesNotOpenThePanel() =
        runTest {
            repository.reload()
            vm.open()
            vm.actAs("b")
            assertEquals(PinPrompt("Bea’s PIN", newPin = false), vm.pin.value)
            vm.enterPin("0000")
            assertIs<ManageUiState.ChoosingActor>(vm.state.value)
            assertEquals("Wrong PIN.", vm.pin.value?.error)
        }

    @Test
    fun theAdminSeesEveryOtherGrownUpAndOnlyItsOwnKids() =
        runTest {
            val panel = panelAs("a", "1234")
            assertEquals("As andre", managingAs(panel.actor.name))
            assertEquals(listOf("b"), panel.grownUps.map { it.id })
            assertEquals(listOf("m", "o"), panel.kids.map { it.id })
            assertEquals(true, panel.canAddGrownUp)
        }

    @Test
    fun aParentSeesItsOwnKidsAndNoGrownUps() =
        runTest {
            val panel = panelAs("b", "5678")
            assertEquals(listOf("t"), panel.kids.map { it.id })
            assertEquals(emptyList(), panel.grownUps)
            assertEquals(false, panel.canAddGrownUp)
        }

    /** A new kid starts at the stricter limit — the add-kid form's default — and its parent raises it. */
    @Test
    fun aKidIsAddedUnderTheParentAtSixAndItsLimitChanged() =
        runTest {
            panelAs("b", "5678")
            vm.addKid("  Lina ")
            val lina = managing().kids.single { it.name == "Lina" }
            assertEquals(6 to "b", lina.kidsAge to lina.parentId)
            vm.setKidsAge(lina.id, 12)
            assertEquals(12, managing().kids.single { it.id == lina.id }.kidsAge)
            assertEquals(2, sync.soonCalls)
        }

    @Test
    fun removingAGrownUpTakesItsKidsWithIt() =
        runTest {
            panelAs("a", "1234")
            vm.remove("b")
            assertEquals(listOf("a", "m", "o"), repository.profiles.value.map { it.id })
            assertNull(managing().notice)
        }

    @Test
    fun aRefusalIsSaidAndThePanelStays() =
        runTest {
            panelAs("a", "1234")
            vm.remove("a")
            assertEquals("That is not allowed.", managing().notice)
            vm.addKid("bea")
            assertEquals("A profile with that name already exists.", managing().notice)
            vm.addKid("Lina", 6)
            assertNull(managing().notice, "a change that took clears the last refusal")
        }

    /** Every change carries the PIN held here; refused as wrong, it is no longer theirs — so ask who they are again. */
    @Test
    fun aHeldPinChangedElsewhereSendsTheViewerBackToWhoAreYou() =
        runTest {
            panelAs("b", "5678")
            core.roles.pins["b"] = "9999"
            vm.setKidsAge("t", 6)
            val asked = assertIs<ManageUiState.ChoosingActor>(vm.state.value)
            assertEquals("Your PIN is no longer valid. Choose who you are again.", asked.notice)
            assertEquals(12, repository.profiles.value.single { it.id == "t" }.kidsAge)
        }

    @Test
    fun aNewGrownUpsFirstPinIsTypedTwice() =
        runTest {
            panelAs("a", "1234")
            vm.addGrownUp("Cleo")
            assertEquals(PinPrompt("A PIN for Cleo", newPin = true), vm.pin.value)
            vm.enterPin("2468")
            vm.enterPin("2468")
            assertEquals("2468", core.roles.pins[repository.profiles.value.single { it.name == "Cleo" }.id])
            assertEquals(1, sync.soonCalls)
        }

    /** A new PIN refused ends its prompt; the panel says why, as the web's `report` does. */
    @Test
    fun aRefusedNewGrownUpIsSaidOnThePanel() =
        runTest {
            panelAs("a", "1234")
            vm.addGrownUp("Bea")
            vm.enterPin("2468")
            vm.enterPin("2468")
            assertNull(vm.pin.value)
            assertEquals("A profile with that name already exists.", managing().notice)
        }

    @Test
    fun changingYourOwnPinKeepsThePanelWorking() =
        runTest {
            panelAs("b", "5678")
            vm.changePin("b")
            assertEquals(PinPrompt("Your new PIN", newPin = true), vm.pin.value)
            vm.enterPin("1357")
            vm.enterPin("1357")
            vm.setKidsAge("t", 6)
            assertNull(managing().notice)
            assertEquals(6, repository.profiles.value.single { it.id == "t" }.kidsAge)
        }

    @Test
    fun theAdminResetsAnotherGrownUpsPin() =
        runTest {
            panelAs("a", "1234")
            vm.changePin("b")
            assertEquals(PinPrompt("A new PIN for Bea", newPin = true), vm.pin.value)
            vm.enterPin("8642")
            vm.enterPin("8642")
            assertEquals("8642", core.roles.pins["b"])
        }

    @Test
    fun aGrownUpWithNoPinSetsOneBeforeManaging() =
        runTest {
            core.roles.pins.remove("b")
            repository.reload()
            vm.open()
            vm.actAs("b")
            assertEquals(PinPrompt("Choose a PIN for Bea", newPin = true), vm.pin.value)
            vm.enterPin("9999")
            vm.enterPin("9999")
            assertEquals("b", managing().actor.id)
            assertEquals("9999", core.roles.pins["b"])
        }

    @Test
    fun closingForgetsThePin() =
        runTest {
            panelAs("b", "5678")
            vm.close()
            assertIs<ManageUiState.Closed>(vm.state.value)
            vm.setKidsAge("t", 6)
            assertEquals(12, repository.profiles.value.single { it.id == "t" }.kidsAge)
        }
}

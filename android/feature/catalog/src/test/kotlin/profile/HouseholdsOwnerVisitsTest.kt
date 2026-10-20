package catalog.profile

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import model.HouseholdWords
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** How long the owner's part stays open and holds its PIN, and when it asks one more, over [HouseholdsOwnerHarness]. */
class HouseholdsOwnerVisitsTest : HouseholdsOwnerHarness() {
    private val ownersPin = PinPrompt(HouseholdWords.OWNERS_PIN, newPin = false)

    @Test
    fun nothingShowsBeforeTheHouseholdsAreRead() =
        runTest(UnconfinedTestDispatcher()) {
            core.homes.names.clear()
            assertEquals(HouseholdsOwnerUiState.Loading, owning().state.value)
        }

    /** A stop closes the part — backgrounding, a rotation, the TV asleep — and the watching owner finds it open again. */
    @Test
    fun theWatchingOwnerSeesThePartOnEveryVisit() =
        runTest(UnconfinedTestDispatcher()) {
            core.chosen = "a"
            val vm = owning()
            vm.part()
            assertNull(vm.pin.value)
            vm.rename("barn", "Shed")
            assertEquals(ownersPin, vm.pin.value)
            vm.enterPin("1234")
            assertEquals("Shed", household("barn")?.name)

            vm.close()
            vm.part()
            vm.rename("barn", "Loft")
            assertEquals(ownersPin, vm.pin.value)
            assertEquals("Shed", household("barn")?.name)
        }

    @Test
    fun aWrongHeldPinAsksAgainAtTheNextChange() =
        runTest(UnconfinedTestDispatcher()) {
            val vm = opened()
            // Changed on another device since it was given here.
            core.roles.pins["a"] = "9999"
            vm.rename("barn", "Shed")
            assertEquals("Wrong PIN.", vm.part().notice)
            assertNull(vm.pin.value)
            vm.rename("barn", "Shed")
            assertEquals(ownersPin, vm.pin.value)
            vm.enterPin("9999")
            assertEquals("Shed", household("barn")?.name)
        }

    /** Into a household nobody runs, the one moved runs it, so needs a PIN; into one with an admin, none is asked. */
    @Test
    fun aMoveIntoAHouseholdNobodyRunsAsksTheMoversFirstPin() =
        runTest(UnconfinedTestDispatcher()) {
            val vm = opened()
            vm.moveHere("home", "c")
            assertNull(vm.pin.value)
            assertEquals("home", profile("c").household)
            vm.moveHere("barn", "c")
            assertEquals(PinPrompt("A PIN for Cy", newPin = true), vm.pin.value)
            vm.enterPin("2468")
            vm.enterPin("2468")
            assertEquals("c", household("barn")?.adminId)
            assertNull(vm.part().notice)
        }

    @Test
    fun aGrownUpWithNoPinIsAskedOneToRunANewHousehold() =
        runTest(UnconfinedTestDispatcher()) {
            val vm = opened()
            vm.create("Loft", adminId = "c", adminName = null)
            assertEquals(PinPrompt("A PIN for Cy", newPin = true), vm.pin.value)
            vm.enterPin("2468")
            vm.enterPin("2468")
            assertEquals("c", households.view.value.list.single { it.name == "Loft" }.adminId)
        }

    /** A change still out when the part closes answers nothing on the next visit, and does not leave it sending. */
    @Test
    fun anAnswerAfterCloseStaysWithItsVisit() =
        runTest(UnconfinedTestDispatcher()) {
            val vm = opened()
            val door = CompletableDeferred<Unit>()
            var held = true
            provider.beforeCore = {
                if (held) {
                    held = false
                    door.await()
                }
            }
            vm.rename("barn", "Flat")
            assertTrue(vm.part().sending)

            vm.close()
            vm.open()
            vm.enterPin("1234")
            door.complete(Unit)

            val part = vm.part()
            assertNull(part.notice)
            assertEquals(0, part.answers)
            assertEquals(false, part.sending)
        }
}

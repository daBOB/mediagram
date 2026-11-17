package catalog.profile

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import model.HouseholdWords
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * How long the owner's view holds the owner's PIN, over [HouseholdsOwnerHarness]'s
 * install: for the whole visit, across the household steps opened and left
 * inside it, through a wrong PIN, until the page is left.
 */
class OwnerViewPinTest : HouseholdsOwnerHarness() {
    private val ownersPin = PinPrompt(HouseholdWords.OWNERS_PIN, newPin = false)

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

    /** The television's households panel closes the part as it goes, while Manage still shows the owner's view. */
    @Test
    fun leavingAHouseholdStepKeepsTheOwnerViewAndItsPinUntilThePageIsLeft() =
        runTest(UnconfinedTestDispatcher()) {
            val (manage, owners) = watchedByTheOwner()
            manage.setKidsAge("m", 12)
            manage.enterPin("1234")
            owners.close()
            owners.rename("barn", "Shed")
            assertNull(owners.pin.value)
            assertEquals("Shed", household("barn")?.name)
            assertEquals("andre", manage.view().actor.name)
            // The page left, both close; back, the owner's view asks the PIN again at the first change.
            manage.close()
            owners.close()
            manage.open(profile("a"))
            owners.rename("barn", "Loft")
            assertEquals(ownersPin, owners.pin.value)
            assertEquals("Shed", household("barn")?.name)
            owners.cancelPin()
            // Opened again at Who are you?, nothing of the owner's view is left.
            manage.open()
            assertEquals(HouseholdsOwnerUiState.Closed(canOpen = true), owners.state.value)
        }
}

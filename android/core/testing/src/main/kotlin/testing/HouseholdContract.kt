package testing

import kotlinx.coroutines.runBlocking
import model.HOME_HOUSEHOLD
import org.junit.Test
import uniffi.mediagram_core.CoreInterface
import uniffi.mediagram_core.Household
import uniffi.mediagram_core.NewHouseholdAdmin
import uniffi.mediagram_core.Profile
import uniffi.mediagram_core.ProfileOutcome
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The households half of [CoreContract], which extends it, so both of its
 * runners hold [FakeCore] and the real core to these cases too: which
 * profiles a device lists, joining a household, and the owner's actions,
 * each refused in the core's order (`state/households/manage.rs`). Every
 * case starts as an install does: its first grown-up, made by
 * `createFirstAdmin`, is the owner and runs Home.
 */
abstract class HouseholdContract {
    /** A fresh core; [CoreContract] says what that is. */
    abstract fun core(synced: Boolean = true): CoreInterface

    private companion object {
        const val PIN = "1234"
        const val BO_PIN = "5678"
        const val WRONG_PIN = "0000"
    }

    @Test
    fun profilesAreTheJoinedHouseholds() {
        runBlocking {
            val core = core()
            val ada = core.owner()
            assertTrue(core.chooseProfile(ada.id))
            val flat = core.flat(ada)
            val bo = core.households().everyone.single { it.name == "Bo" }

            assertEquals(HOME_HOUSEHOLD, core.households().joined)
            assertEquals(listOf("Ada"), core.profiles().map { it.name })
            assertEquals(listOf("Ada", "Bo"), core.households().everyone.map { it.name })
            assertEquals(flat.id, bo.household)
            assertEquals(bo.id, flat.adminId)
            assertEquals(ada.id, core.chosenProfile())

            assertEquals(ProfileOutcome.Done, core.joinHousehold(flat.id, BO_PIN))
            // A kid lands in the household of the grown-up who makes it.
            assertEquals(ProfileOutcome.Done, core.createKid(bo.id, BO_PIN, "Mo", 6u, null))

            assertEquals(flat.id, core.households().joined)
            assertEquals(listOf("Bo", "Mo"), core.profiles().map { it.name })
            // Ada is still the one chosen, but is not here.
            assertNull(core.chosenProfile())
        }
    }

    @Test
    fun joiningTakesTheAdminsOrTheOwnersPin() {
        runBlocking {
            val core = core()
            val ada = core.owner()
            val flat = core.flat(ada)
            val bo = core.households().everyone.single { it.name == "Bo" }

            assertEquals(ProfileOutcome.NotFound, core.joinHousehold("no-such-household", PIN))
            assertEquals(ProfileOutcome.WrongPin, core.joinHousehold(flat.id, WRONG_PIN))
            assertEquals(ProfileOutcome.Done, core.joinHousehold(flat.id, BO_PIN))
            assertEquals(ProfileOutcome.WrongPin, core.joinHousehold(HOME_HOUSEHOLD, BO_PIN))
            assertEquals(ProfileOutcome.Done, core.joinHousehold(HOME_HOUSEHOLD, PIN))
            assertEquals(ProfileOutcome.Done, core.joinHousehold(flat.id, PIN))

            // Wrong guesses at a household's door wait there, and never lock anybody out of their own profile.
            repeat(5) { assertEquals(ProfileOutcome.WrongPin, core.joinHousehold(flat.id, WRONG_PIN)) }
            val waiting = core.joinHousehold(flat.id, BO_PIN)
            assertTrue(waiting is ProfileOutcome.Wait && waiting.seconds in 1u..60u, "$waiting")
            assertEquals(ProfileOutcome.Done, core.unlockProfile(bo.id, BO_PIN))
            assertEquals(ProfileOutcome.Done, core.joinHousehold(HOME_HOUSEHOLD, PIN))
        }
    }

    /** Refused in the core's order: input, a name taken, somebody or a household not there, the PIN, the rule. */
    @Test
    fun ownerActionsAreTheOwners() {
        runBlocking {
            val core = core()
            val ada = core.owner()
            assertEquals(ProfileOutcome.Done, core.createGrownUp(ada.id, PIN, "Cy", PIN))
            val flat = core.flat(ada)
            val (bo, cy) = core.households().everyone.let { all -> all.single { it.name == "Bo" } to all.single { it.name == "Cy" } }

            assertEquals(ProfileOutcome.NotAllowed, core.createHousehold(bo.id, BO_PIN, "Shed", NewHouseholdAdmin.New("Dee", PIN)))
            assertEquals(ProfileOutcome.NotAllowed, core.renameHousehold(cy.id, PIN, flat.id, "Loft"))
            assertEquals(ProfileOutcome.NotAllowed, core.renameHousehold(bo.id, BO_PIN, flat.id, "Loft"))
            assertEquals(ProfileOutcome.NotAllowed, core.setHouseholdAdmin(bo.id, BO_PIN, flat.id, bo.id, null))
            assertEquals(ProfileOutcome.NotAllowed, core.moveToHousehold(bo.id, BO_PIN, flat.id, cy.id, null))
            assertEquals(ProfileOutcome.NotAllowed, core.deleteHousehold(bo.id, BO_PIN, flat.id))

            assertEquals(ProfileOutcome.Invalid, core.moveToHousehold(bo.id, BO_PIN, flat.id, cy.id, "12"))
            assertEquals(ProfileOutcome.NameTaken, core.renameHousehold(bo.id, BO_PIN, flat.id, " home "))
            assertEquals(ProfileOutcome.NotFound, core.renameHousehold(ada.id, WRONG_PIN, "no-such-household", "Loft"))
            assertEquals(ProfileOutcome.WrongPin, core.renameHousehold(bo.id, WRONG_PIN, flat.id, "Loft"))

            assertEquals(ProfileOutcome.Done, core.renameHousehold(ada.id, PIN, flat.id, "Loft"))
            assertEquals(ProfileOutcome.Done, core.moveToHousehold(ada.id, PIN, flat.id, cy.id, null))
            assertEquals(ProfileOutcome.Done, core.setHouseholdAdmin(ada.id, PIN, flat.id, cy.id, null))
            assertEquals(listOf(Household(flat.id, "Loft", cy.id)), core.households().households.filter { it.id == flat.id })
            // Not while anybody is in it.
            assertEquals(ProfileOutcome.NotAllowed, core.deleteHousehold(ada.id, PIN, flat.id))

            assertEquals(ProfileOutcome.Done, core.moveToHousehold(ada.id, PIN, HOME_HOUSEHOLD, bo.id, null))
            // Its admin leaves only as its last grown-up.
            assertEquals(ProfileOutcome.Done, core.moveToHousehold(ada.id, PIN, HOME_HOUSEHOLD, cy.id, null))
            assertEquals(ProfileOutcome.Done, core.deleteHousehold(ada.id, PIN, flat.id))
            assertEquals(listOf("Home"), core.households().households.map { it.name })
        }
    }

    /** The install's first grown-up: its owner, running Home. */
    private suspend fun CoreInterface.owner(): Profile {
        assertEquals(ProfileOutcome.Done, createFirstAdmin("Ada", PIN))
        return profiles().single().also { assertTrue(it.owner && it.admin, "$it") }
    }

    /** A household named Flat, run by a new grown-up, Bo. */
    private suspend fun CoreInterface.flat(owner: Profile): Household {
        assertEquals(ProfileOutcome.Done, createHousehold(owner.id, PIN, "Flat", NewHouseholdAdmin.New("Bo", BO_PIN)))
        return households().households.single { it.name == "Flat" }
    }
}

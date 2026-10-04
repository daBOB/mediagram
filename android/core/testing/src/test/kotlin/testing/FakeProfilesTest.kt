package testing

import kotlinx.coroutines.runBlocking
import org.junit.Test
import uniffi.mediagram_core.Profile
import uniffi.mediagram_core.ProfileOutcome
import kotlin.test.assertEquals

/**
 * What [CoreContract] cannot ask of both cores: a household seeded the way a
 * device from before PINs holds one, and the wrong-PIN wait running out on a
 * clock the test moves — so the fake keeps the real core's rules there too.
 */
class FakeProfilesTest {
    /** Two grown-ups from before PINs, nobody the admin, and a kid. */
    private fun household() =
        FakeCore().apply {
            profiles = listOf(Profile("a", "Ana"), Profile("b", "Bo"), Profile("k", "Kim", kids = true))
        }

    @Test
    fun theFirstClaimMakesTheOnlyAdminAndAPinLessGrownUpTakesThePinGiven() {
        runBlocking {
            val core = household()
            assertEquals(ProfileOutcome.Invalid, core.claimAdmin("a", "12"))
            assertEquals(ProfileOutcome.NotAllowed, core.claimAdmin("k", "1234"))
            assertEquals(ProfileOutcome.Done, core.claimAdmin("a", "1234"))
            assertEquals(listOf("a"), core.profiles().filter { it.admin }.map { it.id })
            assertEquals(ProfileOutcome.NotAllowed, core.claimAdmin("b", "5678"))
            assertEquals(ProfileOutcome.Done, core.unlockProfile("a", "1234"))
        }
    }

    @Test
    fun aGrownUpFromBeforePinsSetsItsFirstWithNothingToProve() {
        runBlocking {
            val core = household()
            assertEquals(ProfileOutcome.NoPin, core.unlockProfile("b", "1234"))
            assertEquals(ProfileOutcome.Done, core.setPin("b", "", "b", "5678"))
            assertEquals(true, core.profiles().single { it.id == "b" }.hasPin)
            assertEquals(ProfileOutcome.WrongPin, core.setPin("b", "", "b", "1111"))
        }
    }

    @Test
    fun theWaitIsThatProfilesAndEndsOnTheClock() {
        runBlocking {
            val core = household()
            core.claimAdmin("a", "1234")
            core.setPin("b", "", "b", "5678")
            repeat(5) { assertEquals(ProfileOutcome.WrongPin, core.unlockProfile("a", "0000")) }
            assertEquals(ProfileOutcome.Wait(60u), core.unlockProfile("a", "1234"))
            assertEquals(ProfileOutcome.Done, core.unlockProfile("b", "5678"))
            core.roles.nowMs += 59_001
            assertEquals(ProfileOutcome.Wait(1u), core.unlockProfile("a", "1234"))
            core.roles.nowMs += 999
            assertEquals(ProfileOutcome.Done, core.unlockProfile("a", "1234"))
        }
    }

    /** A kid's seeded flags never make it the admin or a profile with a PIN, as the real core reads its row. */
    @Test
    fun aKidIsNeverShownAsTheAdminOrWithAPin() {
        runBlocking {
            val core = FakeCore().apply { profiles = listOf(Profile("k", "Kim", kids = true, admin = true)) }
            core.roles.pins["k"] = "1234"
            assertEquals(Profile("k", "Kim", kids = true, kidsAge = 12u), core.profiles().single())
        }
    }
}

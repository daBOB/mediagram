package data

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import model.CreateHousehold
import model.DeleteHousehold
import model.JoinHousehold
import model.MoveToHousehold
import model.ProfileOutcome
import model.RenameHousehold
import model.SetHouseholdAdmin
import uniffi.mediagram_core.CoreInterface
import uniffi.mediagram_core.NewHouseholdAdmin
import kotlin.test.Test
import kotlin.test.assertEquals
import uniffi.mediagram_core.ProfileOutcome as CoreOutcome

/**
 * Each household request against a strict mock: a call it was not told
 * about, or one with other arguments, throws. Every call answers a different
 * outcome, so the request is seen to reach its own.
 */
class CoreProfileCallsTest {
    private val core = mockk<CoreInterface>()

    @Test
    fun eachHouseholdRequestIsItsCoreCall() =
        runTest {
            coEvery { core.joinHousehold("h", "1111") } returns CoreOutcome.Done
            coEvery { core.createHousehold("a", "1111", "Flat", NewHouseholdAdmin.Existing("b", null)) } returns CoreOutcome.NameTaken
            coEvery { core.createHousehold("a", "1111", "Loft", NewHouseholdAdmin.New("Cy", "2222")) } returns CoreOutcome.Invalid
            coEvery { core.renameHousehold("a", "1111", "h", "Loft") } returns CoreOutcome.NotFound
            coEvery { core.setHouseholdAdmin("a", "1111", "h", "b", "2222") } returns CoreOutcome.NoPin
            coEvery { core.moveToHousehold("a", "1111", "h", "b", null) } returns CoreOutcome.WrongPin
            coEvery { core.moveToHousehold("a", "1111", "h", "c", "2222") } returns CoreOutcome.NotAllowed
            coEvery { core.deleteHousehold("a", "1111", "h") } returns CoreOutcome.Wait(3u)

            assertEquals(ProfileOutcome.Done, JoinHousehold("h", "1111").sendTo(core))
            assertEquals(ProfileOutcome.NameTaken, CreateHousehold("a", "1111", "Flat", adminId = "b", adminName = null, newPin = null).sendTo(core))
            assertEquals(ProfileOutcome.Invalid, CreateHousehold("a", "1111", "Loft", adminId = null, adminName = "Cy", newPin = "2222").sendTo(core))
            assertEquals(ProfileOutcome.NotFound, RenameHousehold("a", "1111", "h", "Loft").sendTo(core))
            assertEquals(ProfileOutcome.NoPin, SetHouseholdAdmin("a", "1111", "h", "b", "2222").sendTo(core))
            assertEquals(ProfileOutcome.WrongPin, MoveToHousehold("a", "1111", "h", "b").sendTo(core))
            assertEquals(ProfileOutcome.NotAllowed, MoveToHousehold("a", "1111", "h", "c", newPin = "2222").sendTo(core))
            assertEquals(ProfileOutcome.Wait(3), DeleteHousehold("a", "1111", "h").sendTo(core))
        }

    /** Never sent: the mock would throw at any call. */
    @Test
    fun aNewAdminWithoutAPinIsInvalid() =
        runTest {
            assertEquals(ProfileOutcome.Invalid, CreateHousehold("a", "1111", "Loft", adminId = null, adminName = "Cy", newPin = null).sendTo(core))
            // Nobody named to run it, or two at once, is no admin either.
            assertEquals(ProfileOutcome.Invalid, CreateHousehold("a", "1111", "Loft", adminId = null, adminName = null, newPin = "2222").sendTo(core))
            assertEquals(ProfileOutcome.Invalid, CreateHousehold("a", "1111", "Loft", adminId = "b", adminName = "Cy", newPin = "2222").sendTo(core))
        }
}

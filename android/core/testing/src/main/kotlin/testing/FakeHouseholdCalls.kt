package testing

import uniffi.mediagram_core.Household
import uniffi.mediagram_core.HouseholdsView
import uniffi.mediagram_core.NewHouseholdAdmin
import uniffi.mediagram_core.ProfileOutcome

/**
 * The household calls of the generated core, for [FakeCore] to extend: a
 * core holding Home alone, with everyone in it and its admin running it.
 * Every owner action answers [ProfileOutcome.NotAllowed]; joining Home
 * answers [ProfileOutcome.Done], any other household
 * [ProfileOutcome.NotFound]. Kept apart so [FakeCore] does not grow.
 */
abstract class FakeHouseholdCalls : FakeCoreHandle {
    override suspend fun households(): HouseholdsView {
        val everyone = profiles()
        val home = Household(id = HOME, name = "Home", adminId = everyone.firstOrNull { it.admin }?.id)
        return HouseholdsView(joined = HOME, households = listOf(home), everyone = everyone)
    }

    override suspend fun joinHousehold(id: String, pin: String): ProfileOutcome =
        if (id == HOME) ProfileOutcome.Done else ProfileOutcome.NotFound

    override suspend fun createHousehold(actorId: String, pin: String, name: String, admin: NewHouseholdAdmin): ProfileOutcome =
        ProfileOutcome.NotAllowed

    override suspend fun renameHousehold(actorId: String, pin: String, id: String, name: String): ProfileOutcome =
        ProfileOutcome.NotAllowed

    override suspend fun setHouseholdAdmin(actorId: String, pin: String, id: String, adminId: String, newPin: String?): ProfileOutcome =
        ProfileOutcome.NotAllowed

    override suspend fun moveToHousehold(actorId: String, pin: String, id: String, profileId: String, newPin: String?): ProfileOutcome =
        ProfileOutcome.NotAllowed

    override suspend fun deleteHousehold(actorId: String, pin: String, id: String): ProfileOutcome = ProfileOutcome.NotAllowed

    private companion object {
        const val HOME = "home"
    }
}

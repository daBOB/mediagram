package testing

import uniffi.mediagram_core.HouseholdsView
import uniffi.mediagram_core.NewHouseholdAdmin
import uniffi.mediagram_core.Profile
import uniffi.mediagram_core.ProfileOutcome

/**
 * The household calls of the generated core, for [FakeCore] to extend, each
 * answered by [homes]. Kept apart so [FakeCore] does not grow.
 */
abstract class FakeHouseholdCalls : FakeCoreHandle {
    /** Every profile, as stored: [FakeCore.profiles]. */
    abstract var profiles: List<Profile>

    /** The PINs and the profile rules: [FakeCore.roles]. */
    abstract val roles: FakeProfiles

    /** The households, the one this device joined, and the owner's actions on them. */
    val homes = FakeHouseholds(read = { profiles }, write = { profiles = it }, roles = { roles })

    override suspend fun households(): HouseholdsView = homes.view()

    override suspend fun joinHousehold(id: String, pin: String): ProfileOutcome = homes.join(id, pin)

    override suspend fun createHousehold(actorId: String, pin: String, name: String, admin: NewHouseholdAdmin): ProfileOutcome =
        homes.create(actorId, pin, name, admin)

    override suspend fun renameHousehold(actorId: String, pin: String, id: String, name: String): ProfileOutcome =
        homes.rename(actorId, pin, id, name)

    override suspend fun setHouseholdAdmin(actorId: String, pin: String, id: String, adminId: String, newPin: String?): ProfileOutcome =
        homes.setAdmin(actorId, pin, id, adminId, newPin)

    override suspend fun moveToHousehold(actorId: String, pin: String, id: String, profileId: String, newPin: String?): ProfileOutcome =
        homes.move(actorId, pin, id, profileId, newPin)

    override suspend fun deleteHousehold(actorId: String, pin: String, id: String): ProfileOutcome = homes.delete(actorId, pin, id)
}

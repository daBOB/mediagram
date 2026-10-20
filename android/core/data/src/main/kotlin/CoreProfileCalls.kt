package data

import model.CreateHousehold
import model.DeleteHousehold
import model.Households
import model.JoinHousehold
import model.MoveToHousehold
import model.Profile
import model.ProfileOutcome
import model.ProfileRequest
import model.RenameHousehold
import model.SetHouseholdAdmin
import uniffi.mediagram_core.CoreInterface
import uniffi.mediagram_core.HouseholdsView
import uniffi.mediagram_core.NewHouseholdAdmin
import model.Household as ModelHousehold
import uniffi.mediagram_core.Profile as CoreProfile
import uniffi.mediagram_core.ProfileOutcome as CoreOutcome

/** [this] request as the one core call that answers it. */
internal suspend fun ProfileRequest.sendTo(core: CoreInterface): ProfileOutcome =
    when (this) {
        is ProfileRequest.CreateFirstAdmin -> core.createFirstAdmin(name, newPin)
        is ProfileRequest.Unlock -> core.unlockProfile(id, pin)
        is ProfileRequest.ClaimAdmin -> core.claimAdmin(id, pin)
        is ProfileRequest.CreateGrownUp -> core.createGrownUp(actorId, pin, name, newPin)
        is ProfileRequest.CreateKid ->
            ageOrNull(age)?.let { a -> limitOrNull(dailyLimit)?.let { core.createKid(actorId, pin, name, a, it.value) } } ?: CoreOutcome.Invalid
        is ProfileRequest.Remove -> core.deleteProfile(actorId, pin, id)
        is ProfileRequest.SetPin -> core.setPin(actorId, pin, id, newPin)
        is ProfileRequest.SetKidsAge -> ageOrNull(age)?.let { core.setKidsAge(actorId, pin, id, it) } ?: CoreOutcome.Invalid
        is ProfileRequest.SetDailyLimit -> limitOrNull(minutes)?.let { core.setDailyLimit(actorId, pin, id, it.value) } ?: CoreOutcome.Invalid
        is ProfileRequest.SetHiddenSections -> core.setHiddenSections(actorId, pin, id, sections)
        is JoinHousehold -> core.joinHousehold(id, pin)
        is CreateHousehold -> admin()?.let { core.createHousehold(actorId, pin, name, it) } ?: CoreOutcome.Invalid
        is RenameHousehold -> core.renameHousehold(actorId, pin, id, name)
        is SetHouseholdAdmin -> core.setHouseholdAdmin(actorId, pin, id, adminId, newPin)
        is MoveToHousehold -> core.moveToHousehold(actorId, pin, id, profileId, newPin)
        is DeleteHousehold -> core.deleteHousehold(actorId, pin, id)
    }.toModel()

/** Who runs the new household: one already here, or a new one with a first PIN; null for neither, or both. */
private fun CreateHousehold.admin(): NewHouseholdAdmin? {
    val name = adminName ?: return adminId?.let { NewHouseholdAdmin.Existing(it, newPin) }
    return if (adminId == null) newPin?.let { NewHouseholdAdmin.New(name, it) } else null
}

/**
 * An age as the core's unsigned byte, or null when it does not fit one — an
 * `Int` like 262 would otherwise wrap to 6 on the way across and be accepted.
 */
private fun ageOrNull(age: Int): UByte? = age.takeIf { it in 0..UByte.MAX_VALUE.toInt() }?.toUByte()

/** Minutes wrapped, so a null "no limit" stays distinct from a null "does not fit the core's u16". */
private class Minutes(
    val value: UShort?,
)

private fun limitOrNull(minutes: Int?): Minutes? =
    when {
        minutes == null -> Minutes(null)
        minutes in 0..UShort.MAX_VALUE.toInt() -> Minutes(minutes.toUShort())
        else -> null
    }

private fun CoreOutcome.toModel(): ProfileOutcome =
    when (this) {
        CoreOutcome.Done -> ProfileOutcome.Done
        CoreOutcome.Invalid -> ProfileOutcome.Invalid
        CoreOutcome.NameTaken -> ProfileOutcome.NameTaken
        CoreOutcome.NotFound -> ProfileOutcome.NotFound
        is CoreOutcome.Wait -> ProfileOutcome.Wait(seconds.toInt())
        CoreOutcome.NoPin -> ProfileOutcome.NoPin
        CoreOutcome.WrongPin -> ProfileOutcome.WrongPin
        CoreOutcome.NotAllowed -> ProfileOutcome.NotAllowed
        CoreOutcome.NotSynced -> ProfileOutcome.NotSynced
    }

/** The core's profile as this app's own — the one place the two meet, shared with `testing.FakeProfiles`. */
fun CoreProfile.toModel(): Profile = Profile(
        id = id,
        name = name,
        kids = kids,
        kidsAge = kidsAge?.toInt(),
        parentId = parentId,
        admin = admin,
        hasPin = hasPin,
        dailyLimit = dailyLimit?.toInt(),
        hiddenSections = hiddenSections,
        owner = owner,
        household = household,
    )

/** The core's households view as this app's own. */
fun HouseholdsView.toModel(): Households =
    Households(
        joined = joined,
        list = households.map { ModelHousehold(it.id, it.name, it.adminId) },
        everyone = everyone.map { it.toModel() },
    )

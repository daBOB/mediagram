package data

import model.Profile
import model.ProfileOutcome
import model.ProfileRequest
import uniffi.mediagram_core.CoreInterface
import uniffi.mediagram_core.Profile as CoreProfile
import uniffi.mediagram_core.ProfileOutcome as CoreOutcome

/** [this] request as the one core call that answers it. */
internal suspend fun ProfileRequest.sendTo(core: CoreInterface): ProfileOutcome =
    when (this) {
        is ProfileRequest.CreateFirstAdmin -> core.createFirstAdmin(name, newPin)
        is ProfileRequest.Unlock -> core.unlockProfile(id, pin)
        is ProfileRequest.ClaimAdmin -> core.claimAdmin(id, pin)
        is ProfileRequest.CreateGrownUp -> core.createGrownUp(actorId, pin, name, newPin)
        is ProfileRequest.CreateKid -> ageOrNull(age)?.let { core.createKid(actorId, pin, name, it) } ?: CoreOutcome.Invalid
        is ProfileRequest.Remove -> core.deleteProfile(actorId, pin, id)
        is ProfileRequest.SetPin -> core.setPin(actorId, pin, id, newPin)
        is ProfileRequest.SetKidsAge -> ageOrNull(age)?.let { core.setKidsAge(actorId, pin, id, it) } ?: CoreOutcome.Invalid
    }.toModel()

/**
 * An age as the core's unsigned byte, or null when it does not fit one — an
 * `Int` like 262 would otherwise wrap to 6 on the way across and be accepted.
 */
private fun ageOrNull(age: Int): UByte? = age.takeIf { it in 0..UByte.MAX_VALUE.toInt() }?.toUByte()

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
fun CoreProfile.toModel(): Profile = Profile(id, name, kids, kidsAge?.toInt(), parentId, admin, hasPin)

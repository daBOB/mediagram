package testing

import model.HOME_HOUSEHOLD
import model.RoleAction
import uniffi.mediagram_core.Profile
import uniffi.mediagram_core.ProfileOutcome

private val SIX: UByte = 6u
private val TWELVE: UByte = 12u

/** The two limits a kid may have. */
internal val KIDS_AGES = setOf(SIX, TWELVE)

/** The first grown-up, who is the owner and runs Home. */
fun FakeProfiles.createFirst(
    name: String,
    newPin: String,
): ProfileOutcome =
    first(
        { ProfileOutcome.Invalid.takeUnless { validPin(newPin) } },
        { unusable(name) },
        { ProfileOutcome.NotAllowed.takeIf { read().any { !it.kids } } },
        // Last, once everything else says yes, as `manage/create.rs` asks it.
        { ProfileOutcome.NotSynced.takeUnless { synced() } },
    ) ?: add(name, newPin = newPin, admin = true)

/** In [household], or the maker's own; a household nobody runs makes the new grown-up its admin. */
fun FakeProfiles.createGrownUp(
    actorId: String,
    pin: String,
    name: String,
    newPin: String,
    household: String? = null,
): ProfileOutcome {
    val named = household?.takeIf { it.isNotEmpty() }
    val target = named?.let { id -> households().find { it.id == id } }
    return first(
        { ProfileOutcome.Invalid.takeUnless { validPin(newPin) } },
        { unusable(name) },
        { ProfileOutcome.NotFound.takeIf { named != null && target == null } },
        { check(actorId, pin, RoleAction.CREATE_GROWN_UP, named) },
    ) ?: add(name, newPin = newPin, maker = actorId, household = named, runs = target != null && target.adminId == null)
}

/** For [parentId], or the maker; the kid lives in its parent's household. */
fun FakeProfiles.createKid(
    actorId: String,
    pin: String,
    name: String,
    kidsAge: UByte,
    dailyLimit: UShort? = null,
    parentId: String? = null,
): ProfileOutcome {
    val named = parentId?.takeIf { it.isNotEmpty() }
    return first(
        { ProfileOutcome.Invalid.takeUnless { kidsAge in KIDS_AGES } },
        { ProfileOutcome.Invalid.takeUnless { validMinutes(dailyLimit) } },
        { unusable(name) },
        { check(actorId, pin, RoleAction.CREATE_KID, named) },
    ) ?: add(name, kidsAge = kidsAge, maker = named ?: actorId, dailyLimit = dailyLimit?.toUInt())
}

/** [household] overrides the maker's; [runs] makes the new grown-up its household's admin without making it the owner. */
private fun FakeProfiles.add(
    name: String,
    newPin: String? = null,
    admin: Boolean = false,
    kidsAge: UByte? = null,
    maker: String? = null,
    dailyLimit: UInt? = null,
    household: String? = null,
    runs: Boolean = false,
): ProfileOutcome {
    val clean = cleanName(name) ?: return ProfileOutcome.Invalid
    while (read().any { it.id == "p$nextId" }) nextId++
    val id = "p${nextId++}"
    val kids = kidsAge != null
    val placed = household ?: read().find { it.id == maker }?.household ?: HOME_HOUSEHOLD
    write(
        read() +
            Profile(
                id, clean, kids = kids, kidsAge = kidsAge, parentId = maker.takeIf { kids },
                admin = (admin || runs) && !kids, dailyLimit = dailyLimit, owner = admin && !kids, household = placed,
            ),
    )
    newPin?.let { pins[id] = it }
    return ProfileOutcome.Done
}

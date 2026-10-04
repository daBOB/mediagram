package testing

import uniffi.mediagram_core.Profile
import uniffi.mediagram_core.ProfileOutcome
import java.text.Normalizer

/** `pin_wait.rs`: wrong PINs in a row that start a profile's wait, and how long it lasts. */
private const val MAX_WRONG_PINS = 5
private const val WAIT_MS = 60_000L
private const val MS_PER_SECOND = 1_000L

/** `profiles.rs`'s `MAX_NAME`. */
private const val MAX_NAME = 120

private const val PIN_LENGTH = 4

/** The two limits a kid may have. */
private val KIDS_AGES = setOf<UByte>(6u, 12u)

private enum class Action { CREATE_GROWN_UP, CREATE_KID, REMOVE, SET_PIN, SET_KIDS_AGE }

/**
 * [FakeCore]'s household: who may add, remove, enter and re-PIN whom,
 * refusing for the reasons and in the order
 * `crates/mediagram-core/src/state/profiles/manage.rs` does — the web's
 * order — so a test meets what the tablet's core answers. Split out of
 * `FakeCore.kt` to keep that class under detekt's `LargeClass` limit.
 *
 * The profiles themselves stay [FakeCore.profiles], seedable as before; a
 * seeded profile's `admin` flag is its claim. This holds what a [Profile]
 * never says: each grown-up's PIN in [pins] — kept as given, not hashed,
 * since this fake keeps nothing a hash would protect — and the wrong-PIN
 * counts per profile, read against [nowMs], which a test moves rather than
 * waiting. [shown] is a profile as `profiles()` answers it.
 */
class FakeProfiles(
    private val read: () -> List<Profile>,
    private val write: (List<Profile>) -> Unit,
    /** A profile left: whatever was its goes with it, and the choice when it was the one chosen. */
    private val removed: (String) -> Unit,
) {
    /** Each grown-up's PIN, by profile id — what [Profile.hasPin] reads. */
    val pins: MutableMap<String, String> = mutableMapOf()

    /** The wrong-PIN wait's clock, in milliseconds. */
    var nowMs: Long = 0L

    private class Count(var wrong: Int = 0, var until: Long = 0L)

    private val counts = mutableMapOf<String, Count>()

    // Monotonic rather than derived from the list's size: a create after a
    // delete must not reissue an id a removed row once had, and skipped past
    // any id a test seeded, as the real core's fresh ULID per row never collides.
    private var nextId = 1

    /**
     * As `profiles::list` reads a row: a kid always has a limit, 12 where
     * none was chosen, and is never the admin and never has a PIN.
     */
    fun shown(profile: Profile): Profile =
        if (profile.kids) {
            profile.copy(kidsAge = if (profile.kidsAge == SIX) SIX else TWELVE, admin = false, hasPin = false)
        } else {
            profile.copy(kidsAge = null, hasPin = profile.id in pins)
        }

    fun createFirst(
        name: String,
        newPin: String,
    ): ProfileOutcome =
        first(
            { ProfileOutcome.Invalid.takeUnless { validPin(newPin) } },
            { unusable(name) },
            { ProfileOutcome.NotAllowed.takeIf { read().any { !it.kids } } },
        ) ?: add(name, newPin = newPin, admin = true)

    fun createGrownUp(
        actorId: String,
        pin: String,
        name: String,
        newPin: String,
    ): ProfileOutcome =
        first(
            { ProfileOutcome.Invalid.takeUnless { validPin(newPin) } },
            { unusable(name) },
            { check(actorId, pin, Action.CREATE_GROWN_UP, null) },
        ) ?: add(name, newPin = newPin)

    fun createKid(
        actorId: String,
        pin: String,
        name: String,
        kidsAge: UByte,
    ): ProfileOutcome =
        first(
            { ProfileOutcome.Invalid.takeUnless { kidsAge in KIDS_AGES } },
            { unusable(name) },
            { check(actorId, pin, Action.CREATE_KID, null) },
        ) ?: add(name, kidsAge = kidsAge, parentId = actorId)

    /** A grown-up takes the kids it is the parent of with it; a kid takes only itself. */
    fun remove(
        actorId: String,
        pin: String,
        id: String,
    ): ProfileOutcome =
        check(actorId, pin, Action.REMOVE, id) ?: run {
            val grownUp = read().any { it.id == id && !it.kids }
            val gone = read().filter { it.id == id || (grownUp && it.kids && it.parentId == id) }.map { it.id }.toSet()
            write(read().filterNot { it.id in gone })
            gone.forEach { left ->
                pins.remove(left)
                counts.remove(left)
                removed(left)
            }
            ProfileOutcome.Done
        }

    fun setKidsAge(
        actorId: String,
        pin: String,
        id: String,
        kidsAge: UByte,
    ): ProfileOutcome =
        first(
            { ProfileOutcome.Invalid.takeUnless { kidsAge in KIDS_AGES } },
            { check(actorId, pin, Action.SET_KIDS_AGE, id) },
        ) ?: update(id) { it.copy(kidsAge = kidsAge) }

    /** A kid's opens freely; a grown-up's with its PIN. */
    fun unlock(
        id: String,
        pin: String,
    ): ProfileOutcome {
        val target = read().find { it.id == id } ?: return ProfileOutcome.NotFound
        return (if (target.kids) null else prove(target.id, pin)) ?: ProfileOutcome.Done
    }

    /** Once, while nobody is the admin; a grown-up with no PIN yet takes [pin] as its first. */
    fun claimAdmin(
        id: String,
        pin: String,
    ): ProfileOutcome {
        val profiles = read()
        val target = profiles.find { it.id == id }
        val takesPin = target != null && !target.kids && id !in pins
        return first(
            { ProfileOutcome.Invalid.takeIf { takesPin && !validPin(pin) } },
            { ProfileOutcome.NotFound.takeIf { target == null } },
            // A claim a kid's row still holds is no admin: see [shown].
            { ProfileOutcome.NotAllowed.takeIf { target?.kids == true || profiles.any { it.admin && !it.kids } } },
            { if (takesPin) null else prove(id, pin) },
        ) ?: run {
            if (takesPin) pins[id] = pin
            update(id) { it.copy(admin = true) }
        }
    }

    /** A grown-up's own PIN, or — for the admin — anyone's; a grown-up with none yet sets its first unproven. */
    fun setPin(
        actorId: String,
        pin: String,
        id: String,
        newPin: String,
    ): ProfileOutcome {
        val firstPin = actorId == id && id !in pins && read().any { it.id == id && !it.kids }
        return first(
            { ProfileOutcome.Invalid.takeUnless { validPin(newPin) } },
            { check(actorId, pin, Action.SET_PIN, id, unproven = firstPin) },
        ) ?: run {
            pins[id] = newPin
            ProfileOutcome.Done
        }
    }

    /** The first refusal any of [checks] finds, asked in order and no further. */
    private fun first(vararg checks: () -> ProfileOutcome?): ProfileOutcome? = checks.firstNotNullOfOrNull { it() }

    private fun add(
        name: String,
        newPin: String? = null,
        admin: Boolean = false,
        kidsAge: UByte? = null,
        parentId: String? = null,
    ): ProfileOutcome {
        val clean = cleanName(name) ?: return ProfileOutcome.Invalid
        while (read().any { it.id == "p$nextId" }) nextId++
        val id = "p${nextId++}"
        val kids = kidsAge != null
        write(read() + Profile(id, clean, kids = kids, kidsAge = kidsAge, parentId = parentId, admin = admin && !kids))
        newPin?.let { pins[id] = it }
        return ProfileOutcome.Done
    }

    private fun update(
        id: String,
        change: (Profile) -> Profile,
    ): ProfileOutcome {
        write(read().map { if (it.id == id) change(it) else it })
        return ProfileOutcome.Done
    }

    /** A blank name, or one a profile here already answers to — which sync would read as the same viewer. */
    private fun unusable(name: String): ProfileOutcome? {
        val wanted = normalName(name) ?: return ProfileOutcome.Invalid
        return ProfileOutcome.NameTaken.takeIf { read().any { normalName(it.name) == wanted } }
    }

    /** Somebody there, a grown-up acting, its PIN unless [unproven], then the rule. */
    private fun check(
        actorId: String,
        pin: String,
        action: Action,
        targetId: String?,
        unproven: Boolean = false,
    ): ProfileOutcome? {
        val profiles = read().map(::shown)
        val actor = profiles.find { it.id == actorId }
        return first(
            { ProfileOutcome.NotFound.takeIf { actor == null || (targetId != null && profiles.none { it.id == targetId }) } },
            // A kid manages nothing, and has no PIN to prove otherwise with.
            { ProfileOutcome.NotAllowed.takeIf { actor?.kids == true } },
            { if (unproven) null else prove(actorId, pin) },
            { ProfileOutcome.NotAllowed.takeUnless { profiles.allowed(actorId, action, targetId.orEmpty()) } },
        )
    }

    /** No PIN yet; or, only now one is about to be compared, that profile's wait, then the comparison. */
    private fun prove(
        id: String,
        pin: String,
    ): ProfileOutcome? {
        val held = pins[id] ?: return ProfileOutcome.NoPin
        val left = secondsLeft(id)
        return when {
            left > 0 -> ProfileOutcome.Wait(left.toUInt())
            // A PIN that is not four digits can never equal one that is, so it is wrong, and counts.
            pin != held -> ProfileOutcome.WrongPin.also { failed(id) }
            else -> null.also { counts.remove(id) }
        }
    }

    private fun secondsLeft(id: String): Long {
        val count = counts[id]?.takeIf { it.until != 0L } ?: return 0
        val left = count.until - nowMs
        if (left <= 0) counts.remove(id)
        return if (left > 0) (left + MS_PER_SECOND - 1) / MS_PER_SECOND else 0
    }

    private fun failed(id: String) {
        secondsLeft(id)
        val count = counts.getOrPut(id) { Count() }
        count.wrong++
        if (count.wrong >= MAX_WRONG_PINS) count.until = nowMs + WAIT_MS
    }

    private companion object {
        val SIX: UByte = 6u
        val TWELVE: UByte = 12u
    }
}

/** `rules::allowed`, over profiles as [FakeProfiles.shown] reads them. */
private fun List<Profile>.allowed(
    actorId: String,
    action: Action,
    targetId: String,
): Boolean {
    val actor = find { it.id == actorId }?.takeUnless { it.kids } ?: return false
    val target = find { it.id == targetId }
    val owns = { kid: Profile -> ownerOf(kid) == actor.id }
    return when (action) {
        Action.CREATE_GROWN_UP -> actor.admin
        Action.CREATE_KID -> true
        Action.REMOVE -> target != null && !target.admin && if (target.kids) owns(target) else actor.admin && target.id != actor.id
        Action.SET_PIN -> target != null && !target.kids && (target.id == actor.id || actor.admin)
        Action.SET_KIDS_AGE -> target != null && target.kids && owns(target)
    }
}

/** Its parent while that is a grown-up here, else the admin, else nobody. */
private fun List<Profile>.ownerOf(kid: Profile): String? =
    (find { it.id == kid.parentId && !it.kids } ?: find { it.admin && !it.kids })?.id

/** Four ASCII digits, nothing else. */
private fun validPin(pin: String): Boolean = pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }

/** As `clean_name`: edges trimmed, runs of space collapsed, capped; `null` when nothing is left. */
private fun cleanName(name: String): String? =
    name
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .joinToString(" ")
        .take(MAX_NAME)
        .takeIf { it.isNotEmpty() }

/** As `normal_name` over a cleaned name: the identity sync knows a viewer by. */
private fun normalName(name: String): String? =
    cleanName(name)?.let { Normalizer.normalize(it, Normalizer.Form.NFC).trim().lowercase() }?.takeIf { it.isNotEmpty() }

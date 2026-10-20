package testing

import data.toModel
import model.HOME_HOUSEHOLD
import model.HouseholdAction
import model.householdAllowed
import uniffi.mediagram_core.Household
import uniffi.mediagram_core.HouseholdsView
import uniffi.mediagram_core.NewHouseholdAdmin
import uniffi.mediagram_core.Profile
import uniffi.mediagram_core.ProfileOutcome

/**
 * [FakeCore]'s households: which there are, the one this device [joined],
 * joining one, and the owner's actions — refused for the reasons and in the
 * order `crates/mediagram-core/src/state/households/manage.rs` refuses them,
 * every rule from [householdAllowed], so a test meets what the core answers.
 * [HouseholdContract] holds it to the core.
 *
 * Who is where stays on [FakeCore.profiles]: each profile's `household`,
 * and the `admin` flag of the grown-up who runs one. PINs and their waits
 * are [roles]'s; a household's door keeps a wait of its own, as the core's
 * does. [names] and [joined] are written directly to seed a test.
 */
class FakeHouseholds(
    private val read: () -> List<Profile>,
    private val write: (List<Profile>) -> Unit,
    private val roles: () -> FakeProfiles,
) {
    /** Every live household's name, by id. Home is there from the start and never deleted. */
    val names: MutableMap<String, String> = mutableMapOf(HOME_HOUSEHOLD to "Home")

    /** The household this device joined; null asks to join one. */
    var joined: String? = HOME_HOUSEHOLD

    /** [joined] while it is live: whose profiles `profiles()` lists and whose `chosenProfile()` answers. */
    val here: String? get() = joined?.takeIf { it in names }

    // Monotonic and past any id a test seeded, as the core's fresh ULIDs never collide.
    private var nextId = 1

    fun view(): HouseholdsView {
        val everyone = read().map { roles().shown(it) }
        return HouseholdsView(joined = here, households = live(everyone), everyone = everyone)
    }

    /** Its admin's PIN or the owner's opens it; wrong guesses wait at its door, never on anybody's profile. */
    fun join(
        id: String,
        pin: String,
    ): ProfileOutcome {
        val household = live(read()).find { it.id == id } ?: return ProfileOutcome.NotFound
        val owner = read().find { it.owner && !it.kids }?.id
        val held = listOfNotNull(household.adminId, owner).mapNotNull { roles().pins[it] }
        return roles().prove("household:$id", pin, held) ?: ProfileOutcome.Done.also { joined = id }
    }

    /** Run by a grown-up already here, who moves in with their kids, or by a new one. */
    fun create(
        actorId: String,
        pin: String,
        name: String,
        admin: NewHouseholdAdmin,
    ): ProfileOutcome {
        val clean = cleanName(name) ?: return ProfileOutcome.Invalid
        val (adminId, newName, newPin) =
            when (admin) {
                is NewHouseholdAdmin.Existing -> Triple(admin.id, null, admin.newPin)
                is NewHouseholdAdmin.New -> Triple(null, cleanName(admin.name) ?: return ProfileOutcome.Invalid, admin.newPin)
            }
        if (unusable(newPin)) return ProfileOutcome.Invalid
        if (taken(name, except = null) || (newName != null && read().any { normalName(it.name) == normalName(newName) })) {
            return ProfileOutcome.NameTaken
        }
        owned(actorId, pin, HouseholdAction.CREATE, "", adminId)?.let { return it }
        if (pinless(adminId, newPin)) return ProfileOutcome.NoPin
        while ("h$nextId" in names) nextId++
        val id = "h${nextId++}"
        names[id] = clean
        if (newName != null) write(read() + Profile("$id-admin", newName, household = id))
        moveIn(adminId ?: "$id-admin", id, runs = true, newPin)
        return ProfileOutcome.Done
    }

    fun rename(
        actorId: String,
        pin: String,
        id: String,
        name: String,
    ): ProfileOutcome {
        val clean = cleanName(name) ?: return ProfileOutcome.Invalid
        if (taken(name, except = id)) return ProfileOutcome.NameTaken
        owned(actorId, pin, HouseholdAction.RENAME, id, null)?.let { return it }
        names[id] = clean
        return ProfileOutcome.Done
    }

    /** One of the household's own grown-ups; one with no PIN takes [newPin] as their first. */
    fun setAdmin(
        actorId: String,
        pin: String,
        id: String,
        adminId: String,
        newPin: String?,
    ): ProfileOutcome {
        if (unusable(newPin)) return ProfileOutcome.Invalid
        owned(actorId, pin, HouseholdAction.SET_ADMIN, id, adminId)?.let { return it }
        if (pinless(adminId, newPin)) return ProfileOutcome.NoPin
        write(read().map { if (it.household == id && !it.kids) it.copy(admin = it.id == adminId) else it })
        givePin(adminId, newPin)
        return ProfileOutcome.Done
    }

    /** A grown-up and their kids. Into a household nobody runs they run it, and need a PIN: theirs, or [newPin]. */
    fun move(
        actorId: String,
        pin: String,
        id: String,
        profileId: String,
        newPin: String?,
    ): ProfileOutcome {
        if (unusable(newPin)) return ProfileOutcome.Invalid
        owned(actorId, pin, HouseholdAction.MOVE, id, profileId)?.let { return it }
        val runs = live(read()).any { it.id == id && it.adminId == null }
        if (runs && pinless(profileId, newPin)) return ProfileOutcome.NoPin
        moveIn(profileId, id, runs, newPin)
        return ProfileOutcome.Done
    }

    /** An empty household other than Home, for good. */
    fun delete(
        actorId: String,
        pin: String,
        id: String,
    ): ProfileOutcome = owned(actorId, pin, HouseholdAction.DELETE, id, null) ?: ProfileOutcome.Done.also { names.remove(id) }

    /** By name, as the core lists them; each with the grown-up who runs it. */
    private fun live(everyone: List<Profile>): List<Household> =
        names
            .map { (id, name) -> Household(id, name, everyone.find { it.admin && !it.kids && it.household == id }?.id) }
            .sortedWith(compareBy({ normalName(it.name) }, { it.id }))

    /** Somebody there and the household too, a grown-up acting, its PIN, then the household rule. */
    private fun owned(
        actorId: String,
        pin: String,
        action: HouseholdAction,
        householdId: String,
        targetId: String?,
    ): ProfileOutcome? {
        val profiles = read().map { roles().shown(it) }
        val actor = profiles.find { it.id == actorId }
        return when {
            actor == null || (targetId != null && profiles.none { it.id == targetId }) -> ProfileOutcome.NotFound
            action != HouseholdAction.CREATE && householdId !in names -> ProfileOutcome.NotFound
            // A kid manages nothing, and has no PIN to prove otherwise with.
            actor.kids -> ProfileOutcome.NotAllowed
            else ->
                roles().prove(actorId, pin) ?: ProfileOutcome.NotAllowed.takeUnless {
                    profiles.map { it.toModel() }.householdAllowed(names.keys.toList(), actorId, action, householdId, targetId ?: "")
                }
        }
    }

    /** [profileId] and the kids they made, into [householdId]; [runs] makes them its admin, with [newPin] where they have no PIN. */
    private fun moveIn(
        profileId: String,
        householdId: String,
        runs: Boolean,
        newPin: String?,
    ) {
        write(read().map { if (it.id == profileId || (it.kids && it.parentId == profileId)) it.copy(household = householdId, admin = runs && !it.kids) else it })
        if (runs) givePin(profileId, newPin)
    }

    /** A first PIN, where [id] has none; one who has a PIN keeps it. */
    private fun givePin(
        id: String,
        newPin: String?,
    ) {
        if (newPin != null && validPin(newPin)) roles().pins.putIfAbsent(id, newPin)
    }

    /** A new PIN that came and is not one; none at all is [pinless]'s to say, last. */
    private fun unusable(newPin: String?) = newPin != null && !validPin(newPin)

    /** An admin-to-be ([id] null: one not made yet) with no PIN, and none to give them. */
    private fun pinless(
        id: String?,
        newPin: String?,
    ) = (id == null || id !in roles().pins) && !(newPin != null && validPin(newPin))

    /** Whether a live household other than [except] already answers to [name], compared as profile names are. */
    private fun taken(
        name: String,
        except: String?,
    ) = names.any { (id, other) -> id != except && normalName(other) == normalName(name) }
}

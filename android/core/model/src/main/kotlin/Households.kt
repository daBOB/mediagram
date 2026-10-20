package model

/** One live household; [adminId] null is one nobody runs. */
data class Household(
    val id: String,
    val name: String,
    val adminId: String?,
)

/**
 * The install's households as the owner and Join a household see them: the
 * one this device [joined], every live one in [list], and [everyone] in all
 * of them, where the profile list holds only this device's household.
 */
data class Households(
    val joined: String? = null,
    val list: List<Household> = emptyList(),
    val everyone: List<Profile> = emptyList(),
) {
    /** None joined yet, and a sync round heard: before one is, not having joined is only waiting. */
    fun needsJoin(synced: Boolean) = joined == null && synced

    /** The household this device shows. */
    val here: Household? get() = list.find { it.id == joined }

    /** The install's owner, who runs whatever household they are in. Never a kid. */
    val owner: Profile? get() = everyone.find { it.owner && !it.kids }

    /** How many profiles, kids too, are in household [id]. */
    fun members(id: String): Int = everyone.count { it.household == id }
}

/** What the household rule in [householdAllowed] is asked about; [wire] is the web's own name for it. */
enum class HouseholdAction(
    val wire: String,
) {
    CREATE("create-household"),
    RENAME("rename-household"),
    SET_ADMIN("set-household-admin"),
    MOVE("move-to-household"),
    DELETE("delete-household"),
    JOIN("join-household"),
    CLAIM("claim-household"),
}

/**
 * Whether [actorId] may do [action] to household [householdId] (and profile
 * [targetId], where the action has one; "" for a new grown-up), over profiles
 * the core has already placed. The web's `householdAllowed` line for line, the
 * core's `rules.rs` beside it, all three held to `household-rules.json`.
 * Households compare as stored. It decides what is offered; the core decides
 * what is done.
 */
fun List<Profile>.householdAllowed(
    live: List<String>,
    actorId: String,
    action: HouseholdAction,
    householdId: String,
    targetId: String?,
): Boolean {
    val actor = find { it.id == actorId }?.takeUnless { it.kids } ?: return false
    val owner = actor.owner
    val target = find { it.id == targetId && !it.kids }
    // The owner never moves: they run whatever household they are in, so a move
    // would quietly take it from its recorded admin and leave them stranded
    // there. A household's admin moves only as its last grown-up, which empties
    // it for deletion; while others remain, the owner changes the admin first.
    val movable =
        target != null &&
            !target.owner &&
            (!target.admin || none { !it.kids && it.id != target.id && it.household == target.household })

    // A new household's admin is a new grown-up, or one who could be moved there.
    if (action == HouseholdAction.CREATE) return owner && (targetId == "" || movable)
    if (householdId !in live) return false

    return when (action) {
        HouseholdAction.RENAME -> owner
        // The owner runs their own household, so there is no admin there to change.
        HouseholdAction.SET_ADMIN -> owner && actor.household != householdId && target?.household == householdId
        HouseholdAction.MOVE -> owner && movable && target.household != householdId
        HouseholdAction.DELETE -> owner && householdId != HOME_HOUSEHOLD && none { it.household == householdId }
        HouseholdAction.JOIN -> owner || (actor.admin && actor.household == householdId)
        // A household is left with no admin by a race, an older build, or its admin
        // leaving as its last grown-up. A grown-up then moved in becomes its admin
        // (the core does that, not this rule); claiming covers the rest.
        HouseholdAction.CLAIM ->
            actor.household == householdId && none { !it.kids && it.admin && it.household == householdId }
    }
}

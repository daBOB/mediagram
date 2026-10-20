package model

/** What the household rule in [allowed] is asked about; [wire] is the contract's own name for it. */
enum class RoleAction(
    val wire: String,
) {
    CREATE_GROWN_UP("create-grown-up"),
    CREATE_KID("create-kid"),
    REMOVE("remove"),
    SET_PIN("set-pin"),
    SET_KIDS_AGE("set-kids-age"),
    SET_ACCESS("set-access"),
}

/**
 * The household's admin, or null. Only a grown-up is ever the admin: a view
 * can still say a kid is — a grown-up that held the claim and that another
 * device later called a kid — and a kid read as admin would own every kid
 * nobody else does.
 */
fun List<Profile>.admin(): Profile? = firstOrNull { it.admin && !it.kids }

/**
 * Who manages [kid]: its parent while that names a grown-up still here,
 * otherwise the admin. That covers kids made before parents existed and a
 * kid whose parent was removed on this device but came back from another's
 * document — no migration has to guess a parent. Only its own household counts.
 * Null with no admin either.
 */
fun List<Profile>.ownerOf(kid: Profile): String? {
    val here = filter { it.household == kid.household }
    return kid.parentId?.takeIf { id -> here.any { it.id == id && !it.kids } } ?: here.admin()?.id
}

/**
 * The household's one rule — the web's `profiles-rules.ts` and the core's
 * `rules.rs`, all three held to `profile-rules.json`. The core enforces it on
 * every change, so a button left showing still cannot do the thing. Manage
 * profiles draws its own lists, as the web's panel does, and
 * `ManageOffersFixtureTest` holds them to the same fixture.
 */
fun List<Profile>.allowed(
    actorId: String,
    action: RoleAction,
    targetId: String?,
): Boolean {
    val actor = find { it.id == actorId }?.takeUnless { it.kids } ?: return false
    val target = find { it.id == targetId }
    // Households are separate: only the owner manages a profile in another one.
    if (target != null && target.household != actor.household) return actor.owner && ownerReaches(action, target)
    return when (action) {
        RoleAction.CREATE_GROWN_UP -> actor.admin
        RoleAction.CREATE_KID -> true
        RoleAction.REMOVE ->
            target != null &&
                !(target.admin && !target.kids) &&
                if (target.kids) ownerOf(target) == actor.id else actor.admin && target.id != actor.id
        RoleAction.SET_PIN -> target != null && !target.kids && (target.id == actor.id || actor.admin)
        RoleAction.SET_KIDS_AGE -> target != null && target.kids && ownerOf(target) == actor.id
        // A household's admin never narrows its own view.
        RoleAction.SET_ACCESS ->
            target != null &&
                if (target.kids) ownerOf(target) == actor.id else actor.admin && !target.admin
    }
}

/**
 * What the owner may do to anyone in another household, its admin too: remove
 * them, set their sections, a grown-up's PIN, a kid's limits.
 */
private fun ownerReaches(
    action: RoleAction,
    target: Profile,
): Boolean =
    when (action) {
        RoleAction.SET_PIN -> !target.kids
        RoleAction.SET_KIDS_AGE -> target.kids
        RoleAction.REMOVE, RoleAction.SET_ACCESS -> true
        RoleAction.CREATE_GROWN_UP, RoleAction.CREATE_KID -> false
    }

package catalog.profile

import model.Profile

/**
 * One household's part of Manage profiles: household [id] — what a screen
 * keys it by — whose [name] heads it in the owner's view. Both are null where
 * Manage is drawn as it always was. Adding a grown-up ([addsGrownUp]) or a kid
 * ([addsKid]) is offered where it may be: the admin's own household, and in
 * the owner's view every household, a kid only where a grown-up is. There
 * [household] is the owner's actions on it; [kidParents] the grown-ups a new
 * kid may belong to, as the household lists them, [kidParentDefault] — its
 * admin, else its first — chosen; and [people] everyone in it, grown-ups
 * first, as the web's owner view lists them: [grownUps] and [kids] with what
 * may be changed, the owner's own row with their PIN, and [readOnly] the ones
 * the owner may not change, drawn with their line alone. Elsewhere a new kid
 * is its maker's, and the rows are [grownUps] and [kids].
 */
data class ManagePart(
    val id: String?,
    val name: String?,
    val grownUps: List<Profile>,
    val kids: List<Profile>,
    val addsGrownUp: Boolean,
    val addsKid: Boolean,
    val household: HouseholdRow? = null,
    val kidParents: List<Profile> = emptyList(),
    val kidParentDefault: Profile? = null,
    val people: List<Profile> = emptyList(),
    val readOnly: Set<String> = emptySet(),
) {
    /** Grown-ups is drawn with someone in it or a way to add one, as Kids is. */
    val showsGrownUps: Boolean get() = grownUps.isNotEmpty() || addsGrownUp
    val showsKids: Boolean get() = kids.isNotEmpty() || addsKid
}

/**
 * The parts this panel is drawn in — the web's `byHousehold`, and for the
 * owner its `ownerView`. Anyone but the owner gets one part, unnamed, as does
 * the owner before the households are read. The owner's view has every
 * household as its own part, empty ones too: this device's first, then the
 * others in the live order.
 */
fun ManageUiState.Managing.parts(): List<ManagePart> {
    if (!actor.owner || households.isEmpty()) return listOf(ManagePart(null, null, grownUps, kids, canAddGrownUp, addsKid = true))
    // A household a row is in that the list does not name stays visible, after the named ones.
    val ids = (listOf(here) + households.map { it.id } + everyone.map { it.household }).distinct()
    val changed = (listOf(actor) + grownUps + kids).map { it.id }.toSet()
    return ids.map { id ->
        val people = everyone.filter { it.household == id }.sortedBy { it.kids }
        val parents = people.filterNot { it.kids }
        ManagePart(
            id = id,
            name = households.find { it.id == id }?.name ?: id,
            grownUps = grownUps.filter { it.household == id },
            kids = kids.filter { it.household == id },
            addsGrownUp = true,
            // A kid always belongs to a grown-up, so a household with none adds no kid.
            addsKid = parents.isNotEmpty(),
            household = rows.find { it.household.id == id },
            kidParents = parents,
            kidParentDefault = parents.find { it.admin } ?: parents.firstOrNull(),
            people = people,
            readOnly = people.map { it.id }.filterNot { it in changed }.toSet(),
        )
    }
}

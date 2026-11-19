package catalog.profile

import model.Profile

/**
 * One household's part of Manage profiles: household [id] — what a screen
 * keys it by — whose [name] heads it in the owner's view; none for one the
 * list does not name yet, which is drawn unheaded, as on the web. Both are
 * null where Manage is drawn as it always was. Adding a grown-up ([addsGrownUp]) or a kid
 * ([addsKid]) is offered where it may be: the admin's own household, and in
 * the owner's view every household, a kid only where a grown-up is. There
 * [household] is the owner's actions on it; [kidParents] the grown-ups a new
 * kid may belong to, as the household lists them, [kidParentDefault] — its
 * admin, else its first — chosen. Elsewhere a new kid is its maker's.
 * [people] are its calm rows, one per person, as both screens draw them.
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
    val people: List<PersonRowModel> = emptyList(),
) {
    /** Whether the part ends with Add someone: anyone at all may be added to it. */
    val addsSomeone: Boolean get() = addsGrownUp || addsKid
}

/** What a person's row opens: the manager's own PIN, or what may be changed of a grown-up or a kid. */
enum class RowOpens { YOU, GROWN_UP, KID }

/**
 * One person's calm row — the web's `personRow`: [profile], the [line]
 * Everyone here gives them, and what the row [opens]; null for someone the
 * manager may not change, drawn with their line alone.
 */
data class PersonRowModel(
    val profile: Profile,
    val line: String,
    val opens: RowOpens?,
)

/**
 * The parts this panel is drawn in — the web's `byHousehold`, and for the
 * owner its `ownerView`. Anyone but the owner gets one part, unnamed, as does
 * the owner before the households are read: themselves, then the grown-ups
 * and kids they manage. The owner's view has every household as its own part,
 * empty ones too: this device's first, then the others in the live order,
 * each with everyone in it, grown-ups first, changed or not.
 */
fun ManageUiState.Managing.parts(): List<ManagePart> {
    if (!ownersView) {
        return listOf(ManagePart(null, null, grownUps, kids, canAddGrownUp, addsKid = true, people = rowsOf(listOf(actor) + grownUps + kids, everyone)))
    }
    // A household a row is in that the list does not name stays visible, after the named ones.
    val ids = (listOf(here) + households.map { it.id } + everyone.map { it.household }).distinct()
    return ids.map { id ->
        val people = everyone.filter { it.household == id }.sortedBy { it.kids }
        val parents = people.filterNot { it.kids }
        ManagePart(
            id = id,
            name = households.find { it.id == id }?.name,
            grownUps = grownUps.filter { it.household == id },
            kids = kids.filter { it.household == id },
            addsGrownUp = true,
            // A kid always belongs to a grown-up, so a household with none adds no kid.
            addsKid = parents.isNotEmpty(),
            household = rows.find { it.household.id == id },
            kidParents = parents,
            kidParentDefault = parents.find { it.admin } ?: parents.firstOrNull(),
            people = rowsOf(people, people),
        )
    }
}

/**
 * [shown] as rows, each lined as Everyone here lines it among [among], and
 * opening the manager's own PIN, a kid's limits or a grown-up's PIN —
 * nothing for someone the manager may not change.
 */
private fun ManageUiState.Managing.rowsOf(
    shown: List<Profile>,
    among: List<Profile>,
): List<PersonRowModel> {
    val lines = everyoneHere(among).associate { (one, line) -> one.id to line }
    return shown.map { one ->
        val opens =
            when {
                one.id == actor.id -> RowOpens.YOU
                kids.any { it.id == one.id } -> RowOpens.KID
                grownUps.any { it.id == one.id } -> RowOpens.GROWN_UP
                else -> null
            }
        PersonRowModel(one, lines[one.id].orEmpty(), opens)
    }
}

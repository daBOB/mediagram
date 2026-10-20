package catalog.profile

import model.Profile

/**
 * One household's part of Manage profiles: household [id] — what a screen
 * keys it by — whose [name] heads it while the owner manages more than one
 * household. Both are null where Manage is drawn as it always was. Adding a
 * grown-up ([addsGrownUp], for its admin) or a kid ([addsKid]) stays with
 * this device's household: the Households part places people in the others.
 */
data class ManagePart(
    val id: String?,
    val name: String?,
    val grownUps: List<Profile>,
    val kids: List<Profile>,
    val addsGrownUp: Boolean,
    val addsKid: Boolean,
) {
    /** Grown-ups is drawn with someone in it or a way to add one, as Kids is. */
    val showsGrownUps: Boolean get() = grownUps.isNotEmpty() || addsGrownUp
    val showsKids: Boolean get() = kids.isNotEmpty() || addsKid
}

/**
 * The parts this panel is drawn in — the web's `byHousehold`. Anyone but the
 * owner, and the owner of an install with one household, gets one part,
 * unnamed. Otherwise each household is its own part: the actor's first — this
 * device's, as Who are you? offers only its grown-ups — then the others in
 * the live order, and one with nobody in it to manage not at all.
 */
fun ManageUiState.Managing.parts(): List<ManagePart> {
    val mine = actor.household
    if (!actor.owner || households.size < 2) return listOf(ManagePart(null, null, grownUps, kids, canAddGrownUp, addsKid = true))
    // A household a row is in that the list does not name stays visible, after the named ones.
    val ids = (listOf(mine) + households.map { it.id } + (grownUps + kids).map { it.household }).distinct()
    return ids
        .map { id ->
            val name = households.find { it.id == id }?.name ?: id
            ManagePart(id, name, grownUps.filter { it.household == id }, kids.filter { it.household == id }, canAddGrownUp && id == mine, id == mine)
        }.filter { it.showsGrownUps || it.showsKids }
}

package ui.profile

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import catalog.profile.HouseholdRow
import catalog.profile.ManagePart
import catalog.profile.ManageUiState
import catalog.profile.everyoneHere
import model.Profile
import ui.common.profile.ManageActions

/**
 * One household's part of Manage — the web's `ownerView` for the owner, its
 * `byHousehold` for anyone else. In the owner's view its [header] (its name,
 * its line, the ⋯ with the owner's actions on it), then everyone in it,
 * grown-ups first; for anyone else themselves, then the grown-ups and kids
 * they manage, unnamed. Each person is one calm row ([PersonRow]) that opens
 * what the manager may change of them, one person at a time ([open]); someone
 * they may not change ([ManagePart.readOnly]) opens nothing. Then Add someone,
 * which sends the household and, for a kid, the grown-up it belongs to; why
 * the last add from it did not take is said under its form ([adds]).
 */
@Composable
internal fun HouseholdPart(
    part: ManagePart,
    state: ManageUiState.Managing,
    actions: ManageActions,
    header: @Composable (HouseholdRow) -> Unit,
    adds: OwnerAdds,
    open: String?,
    onOpen: (String?) -> Unit,
    onRemove: (Profile) -> Unit,
) {
    val owners = state.ownersView
    val row = part.household
    if (owners) {
        // A household the list does not name yet (a sync mid-way) keeps its people, under its id.
        if (row != null) header(row) else part.name?.let { Section(it, Modifier.semantics { heading() }) }
    }
    val people = if (owners) part.people else listOf(state.actor) + part.grownUps + part.kids
    val lines = everyoneHere(if (owners) part.people else state.everyone).associate { (one, line) -> one.id to line }
    for (one in people) {
        key(one.id) {
            PersonRow(one, lines[one.id].orEmpty(), open == one.id, { onOpen(one.id.takeUnless { open == it }) }, controlsFor(one, part, state, actions, onRemove))
        }
    }
    if (!part.addsGrownUp && !part.addsKid) return
    val form = part.id.orEmpty()
    AddSomeone(
        grownUp = part.addsGrownUp,
        kid = part.addsKid,
        parents = part.kidParents,
        default = part.kidParentDefault,
        household = row?.household?.name,
        took = adds.took(form),
        notice = state.notice.takeIf { adds.answered(state.answers) == form },
        onAddGrownUp = { name ->
            adds.sent(form, state.answers)
            actions.onAddGrownUp(name, part.id)
        },
        onAddKid = { name, age, daily, parent ->
            adds.sent(form, state.answers)
            actions.onAddKid(name, age, daily, parent)
        },
    )
}

/** What opens under [one]'s row: the manager's own PIN, a kid's limits, a grown-up's PIN — nothing for one they may not change. */
private fun controlsFor(
    one: Profile,
    part: ManagePart,
    state: ManageUiState.Managing,
    actions: ManageActions,
    onRemove: (Profile) -> Unit,
): (@Composable ColumnScope.() -> Unit)? =
    when {
        one.id == state.actor.id -> { { YouControls(one, actions) } }
        one.id in part.readOnly -> null
        part.kids.any { it.id == one.id } -> { { KidControls(one, state, actions, onRemove) } }
        part.grownUps.any { it.id == one.id } -> { { GrownUpControls(one, state, actions, onRemove) } }
        else -> null
    }

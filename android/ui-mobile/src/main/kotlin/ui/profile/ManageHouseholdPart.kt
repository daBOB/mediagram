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
import catalog.profile.PersonRowModel
import catalog.profile.RowOpens
import model.Profile
import ui.common.profile.ManageActions

/**
 * One household's part of Manage — the web's `ownerView` for the owner, its
 * `byHousehold` for anyone else. In the owner's view its [header] (its name,
 * its line, the ⋯ with the owner's actions on it), then everyone in it,
 * grown-ups first; for anyone else themselves, then the grown-ups and kids
 * they manage, unnamed. Each person is one calm row ([PersonRow]) that opens
 * what the manager may change of them ([ManagePart.people]), one person at a
 * time ([open]); someone they may not change opens nothing. Then Add someone,
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
        // A household the list does not name yet (a sync mid-way) keeps its people, unheaded, as on the web.
        if (row != null) header(row) else part.name?.let { Section(it, Modifier.semantics { heading() }) }
    }
    for (person in part.people) {
        val id = person.profile.id
        key(id) {
            PersonRow(person.profile, person.line, open == id, { onOpen(id.takeUnless { open == it }) }, controlsFor(person, state, actions, onRemove))
        }
    }
    if (!part.addsSomeone) return
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

/** What opens under [person]'s row, as [PersonRowModel.opens] says: nothing for one the manager may not change. */
private fun controlsFor(
    person: PersonRowModel,
    state: ManageUiState.Managing,
    actions: ManageActions,
    onRemove: (Profile) -> Unit,
): (@Composable ColumnScope.() -> Unit)? {
    val one = person.profile
    return when (person.opens) {
        RowOpens.YOU -> { { YouControls(one, actions) } }
        RowOpens.KID -> { { KidControls(one, state, actions, onRemove) } }
        RowOpens.GROWN_UP -> { { GrownUpControls(one, state, actions, onRemove) } }
        null -> null
    }
}

package ui.tv.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import catalog.profile.ManagePart
import catalog.profile.ManageUiState
import catalog.profile.everyoneHere
import model.HouseholdWords
import model.Profile

/**
 * One household's people on television, as calm rows — the web's
 * `profile-manage-person.js` and the tablet's `HouseholdPart`: in the owner's
 * view everyone in it, grown-ups first; for anyone else themselves, then the
 * grown-ups and kids they manage. Each is one [TvPersonRow] — their name, the
 * line Everyone here gives them, › — whose OK opens what may be changed of
 * them ([onOpen]): the manager's own PIN, a kid's limits, a grown-up's PIN;
 * someone they may not change ([ManagePart.readOnly]) opens nothing. Then one
 * Add someone ([onAdd]) where adding is offered, named for its household
 * where each has its own. [first] is the first row's, [addRequester] Add
 * someone's; [holds] marks the row holding the remote, by person, null for
 * Add someone.
 */
@Composable
internal fun TvManagePart(
    part: ManagePart,
    state: ManageUiState.Managing,
    onOpen: (TvManageStep) -> Unit,
    onAdd: () -> Unit,
    first: FocusRequester? = null,
    holds: (id: String?) -> Modifier = { Modifier },
    addRequester: FocusRequester? = null,
) {
    val owners = state.ownersView
    val people = if (owners) part.people else listOf(state.actor) + part.grownUps + part.kids
    val lines = everyoneHere(if (owners) part.people else state.everyone).associate { (one, line) -> one.id to line }
    people.forEachIndexed { index, one ->
        key(one.id) {
            val step = stepFor(one, part, state)
            TvPersonRow(one.name, lines[one.id].orEmpty(), step?.let { { onOpen(it) } }, holds(one.id), first.takeIf { index == 0 })
        }
    }
    if (part.addsGrownUp || part.addsKid) {
        val named = part.household?.let { "${HouseholdWords.ADD_SOMEONE_TO} ${part.name}" }
        TvPersonRow(HouseholdWords.ADD_SOMEONE, null, onAdd, holds(null), addRequester, mark = "+", description = named)
    }
}

/** What OK on [one]'s row opens: the manager's own PIN, a kid's limits, a grown-up's PIN — nothing for one they may not change. */
private fun stepFor(
    one: Profile,
    part: ManagePart,
    state: ManageUiState.Managing,
): TvManageStep? =
    when {
        one.id == state.actor.id -> TvManageStep.You(one)
        one.id in part.readOnly -> null
        part.kids.any { it.id == one.id } -> TvManageStep.KidActions(one)
        part.grownUps.any { it.id == one.id } -> TvManageStep.GrownUpActions(one)
        else -> null
    }

package ui.tv.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import catalog.profile.ManagePart
import catalog.profile.PersonRowModel
import catalog.profile.RowOpens
import model.HouseholdWords

/**
 * One household's people on television, as calm rows — the web's
 * `profile-manage-person.js` and the tablet's `HouseholdPart`, over the same
 * [ManagePart.people]. Each is one [TvPersonRow] — their name, the line
 * Everyone here gives them, › — whose OK opens what may be changed of them
 * ([onOpen]): the manager's own PIN, a kid's limits, a grown-up's PIN;
 * someone they may not change opens nothing. Then one Add someone ([onAdd])
 * where adding is offered, named for its household where each has its own.
 * [first] is the first row's, [addRequester] Add someone's; [holds] marks
 * the row holding the remote, by person, null for Add someone.
 */
@Composable
internal fun TvManagePart(
    part: ManagePart,
    onOpen: (TvManageStep) -> Unit,
    onAdd: () -> Unit,
    first: FocusRequester? = null,
    holds: (id: String?) -> Modifier = { Modifier },
    addRequester: FocusRequester? = null,
) {
    part.people.forEachIndexed { index, person ->
        key(person.profile.id) {
            val step = stepFor(person)
            TvPersonRow(person.profile.name, person.line, step?.let { { onOpen(it) } }, holds(person.profile.id), first.takeIf { index == 0 })
        }
    }
    if (part.addsSomeone) {
        val named = part.household?.let { "${HouseholdWords.ADD_SOMEONE_TO} ${part.name}" }
        TvPersonRow(HouseholdWords.ADD_SOMEONE, null, onAdd, holds(null), addRequester, mark = "+", description = named)
    }
}

/** What OK on [person]'s row opens, as [PersonRowModel.opens] says: nothing for one the manager may not change. */
private fun stepFor(person: PersonRowModel): TvManageStep? =
    when (person.opens) {
        RowOpens.YOU -> TvManageStep.You(person.profile)
        RowOpens.KID -> TvManageStep.KidActions(person.profile)
        RowOpens.GROWN_UP -> TvManageStep.GrownUpActions(person.profile)
        null -> null
    }

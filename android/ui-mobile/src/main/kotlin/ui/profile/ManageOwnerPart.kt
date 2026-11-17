package ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import catalog.profile.ADD_A_GROWN_UP
import catalog.profile.ADD_A_KID
import catalog.profile.CHANGE_YOUR_PIN
import catalog.profile.HouseholdRow
import catalog.profile.ManagePart
import catalog.profile.ManageUiState
import catalog.profile.NEW_KID_LIMIT
import catalog.profile.everyoneHere
import designsystem.Spacing
import model.DailyLimitWords
import model.HouseholdWords
import model.Profile
import ui.common.profile.ManageActions
import ui.settings.Notice
import ui.settings.QuietPill
import ui.settings.SettingsChip

/**
 * One household in the owner's view of Manage — the web's `ownerView`: its
 * [header] (name, line, the owner's actions on it), then everyone in it,
 * grown-ups first, each with the line Everyone here gives them and what the
 * owner may change — their own PIN on the owner's row, nothing on a row the
 * owner may not change — then adding a grown-up, and a kid where a grown-up
 * is. Each form keeps what was typed until its add went through, and why
 * the last add from here did not take is said under them ([adds]), as the
 * web keeps and says them.
 */
@Composable
internal fun OwnerPartRows(
    part: ManagePart,
    state: ManageUiState.Managing,
    actions: ManageActions,
    header: @Composable (HouseholdRow) -> Unit,
    adds: OwnerAdds,
    onRemove: (Profile) -> Unit,
) {
    val grownUpForm = AddForm(part.id.orEmpty(), kid = false)
    val kidForm = grownUpForm.copy(kid = true)
    val row = part.household
    // A household the list does not name yet (a sync mid-way) keeps its people, under its id.
    if (row != null) header(row) else part.name?.let { Section(it, Modifier.semantics { heading() }) }
    for ((one, line) in everyoneHere(part.people)) {
        key(one.id) {
            when {
                one.id == state.actor.id -> ProfileRow(one.name, line = line) { QuietPill(CHANGE_YOUR_PIN, onClick = { actions.onChangePin(one.id) }, small = true) }
                part.grownUps.any { it.id == one.id } -> GrownUpRow(one, state, actions, onRemove, line)
                part.kids.any { it.id == one.id } -> KidRow(one, state, actions, onRemove, line)
                else -> ProfileRow(one.name, line = line) {}
            }
        }
    }
    if (part.addsGrownUp) {
        NameForm(
            ADD_A_GROWN_UP,
            onSubmit = {
                adds.sent(grownUpForm, state.answers)
                actions.onAddGrownUp(it, part.id)
            },
            modifier = Modifier.padding(top = Spacing.small),
            took = adds.took(grownUpForm),
        )
    }
    if (part.addsKid) AddKidForm(actions, part.kidParents, part.kidParentDefault, adds.took(kidForm)) { adds.sent(kidForm, state.answers) }
    state.notice.takeIf { adds.answered(state.answers)?.part == part.id }?.let { Notice(it, Modifier.padding(top = Spacing.small)) }
}

/**
 * A new kid's name, then its age limit — the stricter to start — and any
 * daily limit, and whose it is: [default], the household's admin or else its
 * first grown-up, or another of [parents] chosen under Belongs to, offered
 * only where there is more than one. With none, the kid is its maker's. It
 * all starts over as it sends — or, given [took], only once an add from it
 * went through ([NameForm]'s rule); [onAdd] runs as it sends.
 */
@Composable
internal fun AddKidForm(
    actions: ManageActions,
    parents: List<Profile>,
    default: Profile?,
    took: Int? = null,
    onAdd: () -> Unit = {},
) {
    var newLimit by rememberSaveable { mutableIntStateOf(NEW_KID_LIMIT) }
    // Saved as 0 for "No limit": no menu entry is zero minutes, and the saver takes no null.
    var newDaily by rememberSaveable { mutableIntStateOf(0) }
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    // One chosen who has left the household since is nobody's choice any more.
    val parent = parents.find { it.id == chosen } ?: default
    val startOver = {
        newLimit = NEW_KID_LIMIT
        newDaily = 0
        chosen = null
    }
    if (took != null) LaunchedEffect(took) { if (took > 0) startOver() }
    NameForm(
        ADD_A_KID,
        onSubmit = { name ->
            onAdd()
            actions.onAddKid(name, newLimit, newDaily.takeIf { it > 0 }, parent?.id)
            if (took == null) startOver()
        },
        modifier = Modifier.padding(top = Spacing.small),
        took = took,
        more = {
            DailyLimitChips(
                newDaily.takeIf { it > 0 },
                "${DailyLimitWords.LABEL} for the new kid",
                { newDaily = it ?: 0 },
                Modifier.padding(top = Spacing.small),
            )
            if (parents.size > 1) BelongsTo(parents, parent?.id) { chosen = it }
        },
    ) { LimitChoice(newLimit, "Age limit for the new kid") { newLimit = it } }
}

/** Belongs to, and a chip for each of [parents], as Create a household's Admin is chosen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BelongsTo(
    parents: List<Profile>,
    chosen: String?,
    onChoose: (String) -> Unit,
) {
    Text(
        HouseholdWords.BELONGS_TO,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.small, bottom = Spacing.extraSmall),
    )
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
        parents.forEach { one -> SettingsChip(one.name, selected = one.id == chosen, onClick = { onChoose(one.id) }) }
    }
}

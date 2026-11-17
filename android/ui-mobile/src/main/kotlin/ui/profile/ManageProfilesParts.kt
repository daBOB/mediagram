package ui.profile

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import catalog.profile.ADD_A_GROWN_UP
import catalog.profile.GROWN_UPS
import catalog.profile.KIDS_SECTION
import catalog.profile.ManagePart
import catalog.profile.ManageUiState
import catalog.profile.REMOVE
import catalog.profile.RESET_PIN
import designsystem.SectionHead
import designsystem.Spacing
import model.DailyLimitWords
import model.Profile
import ui.common.profile.ManageActions
import ui.settings.QuietPill

/**
 * Manage on the phone for anyone but the owner — the web's `byHousehold`:
 * Grown-ups for the admin, each with Reset PIN and Remove, and a way to add
 * one; then Kids, each with its limits and Remove, and a way to add one.
 * Every row has the sections it is shown ([SectionSwitches]) on its own line,
 * as the web's are. The owner's view of every household is [OwnerPartRows].
 */
@Composable
internal fun ManagePartRows(
    part: ManagePart,
    state: ManageUiState.Managing,
    actions: ManageActions,
    onRemove: (Profile) -> Unit,
) {
    if (part.showsGrownUps) {
        Section(GROWN_UPS)
        part.grownUps.forEach { grownUp -> key(grownUp.id) { GrownUpRow(grownUp, state, actions, onRemove) } }
        if (part.addsGrownUp) NameForm(ADD_A_GROWN_UP, onSubmit = { actions.onAddGrownUp(it, part.id) }, modifier = Modifier.padding(top = Spacing.small))
    }
    if (!part.showsKids) return
    Section(KIDS_SECTION)
    part.kids.forEach { kid -> key(kid.id) { KidRow(kid, state, actions, onRemove) } }
    if (part.addsKid) AddKidForm(actions, part.kidParents, part.kidParentDefault)
}

/** A grown-up another may manage: Reset PIN, Remove, and the sections it is shown; [line] says who it is. */
@Composable
internal fun GrownUpRow(
    grownUp: Profile,
    state: ManageUiState.Managing,
    actions: ManageActions,
    onRemove: (Profile) -> Unit,
    line: String? = null,
) {
    ProfileRow(grownUp.name, below = { Sections(grownUp, state, actions) }, line = line) {
        QuietPill(RESET_PIN, onClick = { actions.onChangePin(grownUp.id) }, small = true)
        RemovePill(grownUp, onRemove)
    }
}

/** A kid: its age limit, daily limit, sections, and Remove; [line] says who it is. */
@Composable
internal fun KidRow(
    kid: Profile,
    state: ManageUiState.Managing,
    actions: ManageActions,
    onRemove: (Profile) -> Unit,
    line: String? = null,
) {
    ProfileRow(
        kid.name,
        below = {
            DailyLimitChips(kid.dailyLimit, "${DailyLimitWords.LABEL} for ${kid.name}", { actions.onSetDailyLimit(kid.id, it) })
            Sections(kid, state, actions)
        },
        line = line,
    ) {
        LimitChoice(kid.kidsLimit, "Age limit for ${kid.name}") { age -> actions.onSetKidsAge(kid.id, age) }
        RemovePill(kid, onRemove)
    }
}

/** The sections [profile] is shown, on the rows that carry them — never the actor's own. */
@Composable
private fun Sections(
    profile: Profile,
    state: ManageUiState.Managing,
    actions: ManageActions,
) {
    if (profile in state.sectionRows) SectionSwitches(profile, state.answers) { actions.onSetHiddenSections(profile.id, it) }
}

/** A heading of Manage's: a household's name, Grown-ups, Kids, or Your PIN. */
@Composable
internal fun Section(
    title: String,
    modifier: Modifier = Modifier,
    style: TextStyle = SectionHead,
) {
    Text(title, style = style, modifier = modifier.padding(top = Spacing.large, bottom = Spacing.small))
}

/** Named for whom it removes, so a screen reader does not hear a column of "Remove". */
@Composable
private fun RemovePill(
    profile: Profile,
    onRemove: (Profile) -> Unit,
) = QuietPill(REMOVE, onClick = { onRemove(profile) }, small = true, contentDescription = "$REMOVE ${profile.name}")

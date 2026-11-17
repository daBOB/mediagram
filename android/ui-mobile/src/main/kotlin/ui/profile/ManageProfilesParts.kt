package ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import catalog.profile.CHANGE_YOUR_PIN
import catalog.profile.ManageUiState
import catalog.profile.REMOVE
import catalog.profile.RESET_PIN
import designsystem.LocalCatalogueTones
import designsystem.SectionHead
import designsystem.Spacing
import model.DailyLimitWords
import model.Profile
import model.SectionAccessWords
import ui.common.profile.ManageActions
import ui.settings.QuietPill

/** What a kid's age limit is called where it is set. */
internal const val AGE_LIMIT = "Age limit"

/*
 * What opens under a person's row on Manage — the web's `profile-manage-rows.js`:
 * one's own PIN; a grown-up's sections, PIN and removal; a kid's age and daily
 * limits, sections and removal — each laid out as a short list, what a line
 * sets above its control, the actions last.
 */

/** The manager's own row: their PIN. */
@Composable
internal fun YouControls(
    you: Profile,
    actions: ManageActions,
) = QuietPill(CHANGE_YOUR_PIN, onClick = { actions.onChangePin(you.id) }, small = true)

/** A grown-up another may manage: the sections they are shown, then Reset PIN and Remove. */
@Composable
internal fun GrownUpControls(
    grownUp: Profile,
    state: ManageUiState.Managing,
    actions: ManageActions,
    onRemove: (Profile) -> Unit,
) {
    Sections(grownUp, state, actions)
    Actions(grownUp, onRemove) { QuietPill(RESET_PIN, onClick = { actions.onChangePin(grownUp.id) }, small = true) }
}

/** A kid: its age limit, daily limit and sections, then Remove. */
@Composable
internal fun KidControls(
    kid: Profile,
    state: ManageUiState.Managing,
    actions: ManageActions,
    onRemove: (Profile) -> Unit,
) {
    Line(AGE_LIMIT) { LimitChoice(kid.kidsLimit, "Age limit for ${kid.name}") { age -> actions.onSetKidsAge(kid.id, age) } }
    Line(DailyLimitWords.LABEL) { DailyLimitChips(kid.dailyLimit, "${DailyLimitWords.LABEL} for ${kid.name}", { actions.onSetDailyLimit(kid.id, it) }) }
    Sections(kid, state, actions)
    Actions(kid, onRemove)
}

/** One line of an open part: what it sets, for the eye — each control names itself — then the control. */
@Composable
internal fun Line(
    label: String,
    control: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = LocalCatalogueTones.current.quiet, modifier = Modifier.clearAndSetSemantics {})
        control()
    }
}

/** The sections [profile] is shown, on the rows that carry them — never the actor's own. */
@Composable
private fun Sections(
    profile: Profile,
    state: ManageUiState.Managing,
    actions: ManageActions,
) {
    if (profile in state.sectionRows) Line(SectionAccessWords.TITLE) { SectionSwitches(profile, state.answers) { actions.onSetHiddenSections(profile.id, it) } }
}

/** An open part's actions, under a rule: [first] at the start, removing [profile] apart at the end. */
@Composable
private fun Actions(
    profile: Profile,
    onRemove: (Profile) -> Unit,
    first: @Composable RowScope.() -> Unit = {},
) {
    HorizontalDivider(thickness = 0.5.dp, color = LocalCatalogueTones.current.ruleSoft)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        first()
        Spacer(Modifier.weight(1f))
        // Named for whom it removes, so a screen reader does not hear a column of "Remove".
        QuietPill(REMOVE, onClick = { onRemove(profile) }, small = true, contentDescription = "$REMOVE ${profile.name}")
    }
}

/** A heading of Manage's: a household's name where it has no line of its own. */
@Composable
internal fun Section(
    title: String,
    modifier: Modifier = Modifier,
    style: TextStyle = SectionHead,
) {
    Text(title, style = style, modifier = modifier.padding(top = Spacing.large, bottom = Spacing.small))
}

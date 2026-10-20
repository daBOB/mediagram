package ui.profile

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import catalog.profile.ADD_A_GROWN_UP
import catalog.profile.ADD_A_KID
import catalog.profile.GROWN_UPS
import catalog.profile.KIDS_SECTION
import catalog.profile.ManagePart
import catalog.profile.ManageUiState
import catalog.profile.NEW_KID_LIMIT
import catalog.profile.REMOVE
import catalog.profile.RESET_PIN
import designsystem.SectionHead
import designsystem.Spacing
import model.DailyLimitWords
import model.Profile
import ui.common.profile.ManageActions
import ui.settings.QuietPill

/**
 * One household's part of Manage on the phone — the web's `byHousehold`:
 * under the household's name while the owner manages more than one, a level
 * above its Grown-ups and Kids; each grown-up with Reset PIN and Remove, each
 * kid with its limits and Remove, every row with the sections it is shown
 * ([SectionSwitches]) on its own line, as the web's are. Adding a grown-up or
 * a kid is offered only where [part] says.
 */
@Composable
internal fun ManagePartRows(
    part: ManagePart,
    state: ManageUiState.Managing,
    actions: ManageActions,
    onRemove: (Profile) -> Unit,
) {
    val sub = if (part.name == null) SectionHead else MaterialTheme.typography.titleSmall
    part.name?.let { Section(it, Modifier.semantics { heading() }) }
    val sections: @Composable (Profile) -> Unit = { p -> if (p in state.sectionRows) SectionSwitches(p, state.answers) { actions.onSetHiddenSections(p.id, it) } }
    if (part.showsGrownUps) {
        Section(GROWN_UPS, style = sub)
        part.grownUps.forEach { grownUp ->
            key(grownUp.id) {
                ProfileRow(grownUp.name, below = { sections(grownUp) }) {
                    QuietPill(RESET_PIN, onClick = { actions.onChangePin(grownUp.id) }, small = true)
                    RemovePill(grownUp, onRemove)
                }
            }
        }
        if (part.addsGrownUp) NameForm(ADD_A_GROWN_UP, onSubmit = actions.onAddGrownUp, modifier = Modifier.padding(top = Spacing.small))
    }
    if (!part.showsKids) return
    Section(KIDS_SECTION, style = sub)
    part.kids.forEach { kid ->
        key(kid.id) {
            ProfileRow(
                kid.name,
                below = {
                    DailyLimitChips(kid.dailyLimit, "${DailyLimitWords.LABEL} for ${kid.name}", { actions.onSetDailyLimit(kid.id, it) })
                    sections(kid)
                },
            ) {
                LimitChoice(kid.kidsLimit, "Age limit for ${kid.name}") { age -> actions.onSetKidsAge(kid.id, age) }
                RemovePill(kid, onRemove)
            }
        }
    }
    if (part.addsKid) AddKidForm(actions)
}

/** A new kid's name, then its age limit — the stricter to start — and any daily limit. */
@Composable
private fun AddKidForm(actions: ManageActions) {
    var newLimit by rememberSaveable { mutableIntStateOf(NEW_KID_LIMIT) }
    // Saved as 0 for "No limit": no menu entry is zero minutes, and the saver takes no null.
    var newDaily by rememberSaveable { mutableIntStateOf(0) }
    NameForm(
        ADD_A_KID,
        onSubmit = { name ->
            actions.onAddKid(name, newLimit, newDaily.takeIf { it > 0 })
            newLimit = NEW_KID_LIMIT
            newDaily = 0
        },
        modifier = Modifier.padding(top = Spacing.small),
        more = {
            DailyLimitChips(
                newDaily.takeIf { it > 0 },
                "${DailyLimitWords.LABEL} for the new kid",
                { newDaily = it ?: 0 },
                Modifier.padding(top = Spacing.small),
            )
        },
    ) { LimitChoice(newLimit, "Age limit for the new kid") { newLimit = it } }
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

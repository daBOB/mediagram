package ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import catalog.profile.ADD_A_GROWN_UP
import catalog.profile.ADD_A_KID
import catalog.profile.CHANGE_YOUR_PIN
import catalog.profile.DONE
import catalog.profile.GROWN_UPS
import catalog.profile.KIDS_SECTION
import catalog.profile.MANAGE_PROFILES
import catalog.profile.ManageUiState
import catalog.profile.NEW_KID_LIMIT
import catalog.profile.REMOVE
import catalog.profile.RESET_PIN
import catalog.profile.WHO_ARE_YOU
import catalog.profile.YOUR_PIN
import catalog.profile.managingAs
import designsystem.SectionHead
import designsystem.Spacing
import model.DailyLimitWords
import model.Profile
import ui.common.profile.ManageActions
import ui.settings.QuietPill

/**
 * Manage profiles on the phone — the web's `profile-manage.js`, section for
 * section: who you are; then, for the admin, the other grown-ups and a way to
 * add one; for every grown-up, its own kids at FSK 6 or 12 and a way to add
 * one; and its own PIN. The PIN itself is asked by [PinDialog] over this; a
 * removal asks first ([RemoveProfileDialog]). Why the last change did not
 * take is said above Done, where the web says it.
 *
 * [inline] draws it inside Settings' Profiles section, as the web's tab mounts
 * the same body: its heads in the settings' own face, and no scroll or Done of
 * its own — leaving the section is what ends the visit.
 */
@Composable
internal fun ManageProfilesScreen(
    state: ManageUiState,
    actions: ManageActions,
    inline: Boolean = false,
) {
    val heads = if (inline) SectionHead else MaterialTheme.typography.titleMedium
    when (state) {
        ManageUiState.Closed -> Unit
        is ManageUiState.ChoosingActor -> Page(WHO_ARE_YOU, state.notice, actions.onClose, inline) { WhoAreYou(state.grownUps, actions.onActAs) }
        is ManageUiState.Managing -> Page(managingAs(state.actor.name), state.notice, actions.onClose, inline) { Managing(state, actions, heads) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhoAreYou(
    grownUps: List<Profile>,
    onActAs: (String) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small), modifier = Modifier.padding(top = Spacing.medium)) {
        grownUps.forEach { profile -> QuietPill(profile.name, onClick = { onActAs(profile.id) }) }
    }
}

@Composable
private fun Managing(
    state: ManageUiState.Managing,
    actions: ManageActions,
    heads: TextStyle,
) {
    var removing by remember { mutableStateOf<Profile?>(null) }
    var newLimit by rememberSaveable { mutableIntStateOf(NEW_KID_LIMIT) }
    // Saved as 0 for "No limit": no menu entry is zero minutes, and the saver takes no null.
    var newDaily by rememberSaveable { mutableIntStateOf(0) }
    if (state.canAddGrownUp) {
        Section(GROWN_UPS, heads)
        state.grownUps.forEach { grownUp ->
            ProfileRow(grownUp.name) {
                QuietPill(RESET_PIN, onClick = { actions.onChangePin(grownUp.id) }, small = true)
                RemovePill(grownUp) { removing = it }
            }
        }
        NameForm(ADD_A_GROWN_UP, onSubmit = actions.onAddGrownUp, modifier = Modifier.padding(top = Spacing.small))
    }
    Section(KIDS_SECTION, heads)
    state.kids.forEach { kid ->
        ProfileRow(
            kid.name,
            below = {
                DailyLimitChips(kid.dailyLimit, "${DailyLimitWords.LABEL} for ${kid.name}", { actions.onSetDailyLimit(kid.id, it) })
            },
        ) {
            LimitChoice(kid.kidsLimit, "Age limit for ${kid.name}") { age -> actions.onSetKidsAge(kid.id, age) }
            RemovePill(kid) { removing = it }
        }
    }
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
    Section(YOUR_PIN, heads)
    QuietPill(CHANGE_YOUR_PIN, onClick = { actions.onChangePin(state.actor.id) })
    removing?.let { target -> RemoveProfileDialog(target, onRemove = { actions.onRemove(target.id) }, onDismiss = { removing = null }) }
}

@Composable
private fun Page(
    note: String,
    notice: String?,
    onDone: () -> Unit,
    inline: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(if (inline) Modifier else Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.large)) {
        Text(MANAGE_PROFILES, style = if (inline) SectionHead else MaterialTheme.typography.headlineSmall)
        Text(
            note,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.small),
        )
        content()
        notice?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.large).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (!inline) QuietPill(DONE, onClick = onDone, modifier = Modifier.padding(top = Spacing.large))
    }
}

@Composable
private fun Section(
    title: String,
    style: TextStyle,
) {
    Text(title, style = style, modifier = Modifier.padding(top = Spacing.large, bottom = Spacing.small))
}

/** Named for whom it removes, so a screen reader does not hear a column of "Remove". */
@Composable
private fun RemovePill(
    profile: Profile,
    onRemove: (Profile) -> Unit,
) = QuietPill(REMOVE, onClick = { onRemove(profile) }, small = true, contentDescription = "$REMOVE ${profile.name}")

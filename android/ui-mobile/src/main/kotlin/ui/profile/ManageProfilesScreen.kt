package ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import catalog.profile.HouseholdRow
import catalog.profile.MANAGE_PROFILES
import catalog.profile.ManageUiState
import catalog.profile.managingAs
import catalog.profile.parts
import designsystem.SectionHead
import designsystem.Spacing
import model.Profile
import ui.common.profile.ManageActions
import ui.settings.Notice

/**
 * Manage profiles on the phone — the web's `profile-manage.js`: Who's
 * managing? ([WhoIsManaging]) until a grown-up has said who they are with
 * their PIN; then their household as calm rows, one per person, each opening
 * what their role lets them change, and Add someone ([HouseholdPart]). For the
 * owner, every household instead, each under its [header], then [create], a
 * new household. The PIN itself is asked by [PinDialog] over this; a removal
 * asks first ([RemoveProfileDialog]). Why the last change did not take is said
 * at the end, where the web says it — an add's under its own form. Headings
 * are set in [headStyle]: the picker's over the picker, a section's in Settings.
 *
 * The body of the Profiles page ([ui.settings.ProfilesPage]), wherever that
 * is shown, as the web mounts the same body: no scroll or Done of its own —
 * leaving the page is what ends the visit.
 */
@Composable
internal fun ManageProfilesScreen(
    state: ManageUiState,
    actions: ManageActions,
    header: @Composable (HouseholdRow) -> Unit = { Section(it.household.name, Modifier.semantics { heading() }) },
    create: @Composable () -> Unit = {},
    headStyle: TextStyle = SectionHead,
) {
    when (state) {
        ManageUiState.Closed -> Unit
        is ManageUiState.ChoosingActor -> WhoIsManaging(state, actions.onActAs, headStyle)
        is ManageUiState.Managing -> Managing(state, actions, header, create, headStyle)
    }
}

@Composable
private fun Managing(
    state: ManageUiState.Managing,
    actions: ManageActions,
    header: @Composable (HouseholdRow) -> Unit,
    create: @Composable () -> Unit,
    headStyle: TextStyle,
) {
    var removing by remember { mutableStateOf<Profile?>(null) }
    // Who is open on the page: one person at a time, still open after a change redraws it.
    var open by rememberSaveable(state.actor.id) { mutableStateOf<String?>(null) }
    val parts = state.parts()
    val adds = remember(state.actor.id) { OwnerAdds() }
    LaunchedEffect(state.answers) { adds.heard(state.answers, state.notice) }
    val sends = actions.notingOthers(adds)
    // An add's refusal is said under its form; any other at the end.
    val atAnAdd = adds.answered(state.answers)?.takeIf { form -> parts.any { it.id.orEmpty() == form } }
    Column {
        Text(MANAGE_PROFILES, style = headStyle, modifier = Modifier.semantics { heading() })
        Text(
            managingAs(state.actor.name),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.small),
        )
        Spacer(Modifier.height(Spacing.medium))
        parts.forEach { part -> key(part.id) { HouseholdPart(part, state, sends, header, adds, open, { open = it }) { removing = it } } }
        if (state.ownersView) create()
        state.notice.takeIf { atAnAdd == null }?.let { Notice(it, Modifier.padding(top = Spacing.large)) }
    }
    removing?.let { target -> RemoveProfileDialog(target, onRemove = { sends.onRemove(target.id) }, onDismiss = { removing = null }) }
}

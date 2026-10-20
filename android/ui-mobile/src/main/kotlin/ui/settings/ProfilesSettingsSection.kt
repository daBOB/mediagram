package ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.profile.EVERYONE_HERE
import catalog.profile.ManageProfilesViewModel
import catalog.profile.ManageUiState
import catalog.profile.everyoneHere
import catalog.profile.profileCount
import designsystem.SectionHead
import designsystem.Spacing
import model.Profile
import ui.common.profile.manageActions
import ui.common.settings.IndexStatus
import ui.profile.ManageProfilesScreen
import ui.profile.PinDialog
import ui.profile.ProfileRow

/**
 * Settings › Profiles — the web Settings page's Profiles tab
 * (`settings-profiles.js`): everyone in the household at a glance, then the
 * picker's own Manage profiles, inline. Seeing the list needs no PIN; changing
 * anything asks who you are and that grown-up's PIN, held only for the visit
 * ([ProfilesVisit]).
 */
@Composable
internal fun ProfilesSettingsSection() {
    val manage: ManageProfilesViewModel = hiltViewModel()
    val profiles by manage.profiles.collectAsStateWithLifecycle()
    val state by manage.state.collectAsStateWithLifecycle()
    val pin by manage.pin.collectAsStateWithLifecycle()
    val actions = remember(manage) { manage.manageActions() }
    Column(verticalArrangement = Arrangement.spacedBy(40.dp)) {
        Column {
            Text(EVERYONE_HERE, style = SectionHead, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = Spacing.small))
            for ((profile, line) in everyoneHere(profiles)) {
                ProfileRow(profile.name) {
                    Text(
                        line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        ManageProfilesScreen(state, actions, inline = true)
    }
    pin?.let { PinDialog(it, onPin = manage::enterPin, onDismiss = manage::cancelPin) }
}

/** Profiles' line in the index — how many there are — only while a grown-up is chosen; null leaves the row out. */
@Composable
internal fun profilesStatus(chosen: Profile?): IndexStatus? {
    if (chosen == null || chosen.kids) return null
    val profiles by hiltViewModel<ManageProfilesViewModel>().profiles.collectAsStateWithLifecycle()
    return IndexStatus(profileCount(profiles.size))
}

/**
 * One visit to Profiles while [open]: Manage drops the PIN on the way out — to
 * another section, out of Settings, or out of the app — and asks "Who are
 * you?" whenever it finds itself closed while the section is still open. That
 * covers the way in, the way back from the background, and the gate's own
 * close when it is composed afresh around an open section (the activity
 * recreated, or restored), whichever of the two effects runs first. Held by the
 * frame rather than the page, which a tablet turned between one pane and two
 * draws afresh.
 */
@Composable
internal fun ProfilesVisit(open: Boolean) {
    if (!open) return
    val manage: ManageProfilesViewModel = hiltViewModel()
    val closed = manage.state.collectAsStateWithLifecycle().value == ManageUiState.Closed
    LaunchedEffect(closed) { if (closed) manage.open() }
    LifecycleStartEffect(manage) { onStopOrDispose { manage.close() } }
}

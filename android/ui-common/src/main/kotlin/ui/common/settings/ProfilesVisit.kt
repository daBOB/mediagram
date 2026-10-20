package ui.common.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.profile.ManageProfilesViewModel
import catalog.profile.ManageUiState
import catalog.profile.profileCount
import model.Profile

/**
 * Profiles' line in Settings' index — how many there are — only while a
 * grown-up is chosen; null leaves the row out. A phone and a television ask
 * this alike, so a kid sees the section on neither.
 */
@Composable
fun profilesStatus(chosen: Profile?): IndexStatus? {
    if (chosen == null || chosen.kids) return null
    val profiles by hiltViewModel<ManageProfilesViewModel>().profiles.collectAsStateWithLifecycle()
    return IndexStatus(profileCount(profiles.size))
}

/**
 * One visit to Profiles while [open]: Manage drops the PIN on the way out — to
 * another section, out of Settings, or out of the app — and asks "Who are
 * you?" whenever it finds itself closed while the visit is still open. That
 * covers the way in, the way back from the background, and the gate's own
 * close when it is composed afresh around an open visit (the activity
 * recreated, or restored), whichever of the two effects runs first. Held by
 * whatever outlives the page: the phone's Settings frame, which a tablet
 * turned between one pane and two draws afresh, or the television's Manage
 * panel.
 */
@Composable
fun ProfilesVisit(open: Boolean) {
    if (!open) return
    val manage: ManageProfilesViewModel = hiltViewModel()
    val closed = manage.state.collectAsStateWithLifecycle().value == ManageUiState.Closed
    LaunchedEffect(closed) { if (closed) manage.open() }
    LifecycleStartEffect(manage) { onStopOrDispose { manage.close() } }
}

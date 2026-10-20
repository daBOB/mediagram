package ui.common.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.profile.HouseholdsOwnerViewModel
import catalog.profile.ManageProfilesViewModel
import catalog.profile.ManageUiState
import catalog.profile.profilesLine
import model.Profile

/** Profiles' line in Settings' index ([profilesLine]): who is watching, and how many profiles there are. */
@Composable
fun profilesStatus(chosen: Profile?): IndexStatus {
    val profiles by hiltViewModel<ManageProfilesViewModel>().profiles.collectAsStateWithLifecycle()
    return IndexStatus(profilesLine(chosen, profiles.size))
}

/**
 * One visit to Profiles while [open]: Manage, and the owner's Households part
 * beside it, drop their PINs on the way out — to another section, out of
 * Settings, or out of the app — and Manage asks "Who are you?" whenever it
 * finds itself closed while the visit is still open. That covers the way
 * in, the way back from the background, and the gate's own
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
    val households: HouseholdsOwnerViewModel = hiltViewModel()
    val closed = manage.state.collectAsStateWithLifecycle().value == ManageUiState.Closed
    LaunchedEffect(closed) { if (closed) manage.open() }
    LifecycleStartEffect(manage, households) {
        onStopOrDispose {
            manage.close()
            households.close()
        }
    }
}

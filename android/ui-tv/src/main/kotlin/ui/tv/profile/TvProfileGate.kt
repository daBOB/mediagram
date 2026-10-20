package ui.tv.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.merge
import catalog.profile.HouseholdsOwnerViewModel
import catalog.profile.JoinHouseholdViewModel
import catalog.profile.ManageProfilesViewModel
import catalog.profile.ManageUiState
import catalog.profile.ProfileUiState
import catalog.profile.ProfileViewModel
import ui.common.profile.ForgetManageWhenAway
import ui.common.profile.manageActions
import ui.common.rememberLibraryPositions
import ui.tv.TvCastOverPicker
import ui.tv.rememberCastOpens

/**
 * Who is watching, as the masthead needs it: the name its last entry shows,
 * and what selecting that entry does. The television twin of the phone's
 * `ui.chrome.ProfileBarState`, declared here rather than shared because that one
 * lives in `:ui-mobile`, which this module never sees.
 */
data class TvChosenProfile(
    val name: String,
    val onChoose: () -> Unit,
)

/**
 * The television counterpart to `ui.profile.ProfileGate`: the same
 * [ProfileViewModel] and [ManageProfilesViewModel] decide the same things
 * they decide on a phone, and [TvProfilePicker] stands in for [content] until
 * a viewer has been chosen. Before either, a box that has joined no
 * household shows only Join a household ([JoinHouseholdViewModel]); one
 * whose household has no grown-up left while another has offers it where
 * the first profile — which the core would refuse — would be, beside its
 * kids; and none is offered before the households are read. One screen at a time — whichever PIN is asked
 * replaces what asked for it, the picker's Manage profiles opens Settings' own
 * Profiles page in the picker's place, with nobody chosen, and that page's
 * Manage profiles row replaces it with Manage — so nothing behind competes
 * for the remote. Manage asks who you are and that PIN on every visit, there
 * and in Settings, so entering a profile never hands a child the controls. It
 * closes, forgetting its PIN, when the library shows again or the app is left
 * (`ui.common.profile.ForgetManageWhenAway`).
 *
 * Once a viewer is chosen, [content] is handed it the way the phone's gate
 * hands its bar a `ProfileBarState`: the name, and [ProfileViewModel.reopen]
 * as what choosing it does.
 *
 * With no viewer chosen the TV still answers a cast, and plays it over the
 * picker ([TvCastOverPicker]) until it is over; the library listens for
 * itself once there is one. Not before Join a household, though: until then
 * this device lists nobody for the cast to play as.
 */
@Composable
internal fun TvProfileGate(content: @Composable (TvChosenProfile) -> Unit) {
    val viewModel: ProfileViewModel = hiltViewModel()
    val manage: ManageProfilesViewModel = hiltViewModel()
    val join: JoinHouseholdViewModel = hiltViewModel()
    val owner: HouseholdsOwnerViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val joining by join.state.collectAsStateWithLifecycle()
    val managing by manage.state.collectAsStateWithLifecycle()
    val pickPin by viewModel.pin.collectAsStateWithLifecycle()

    val chosen = state as? ProfileUiState.Chosen
    // The owner's part opens at once only for the owner chosen here: over a picker shown again it is anybody's, as on the web.
    LaunchedEffect(owner, chosen?.profile?.id) { owner.watching(chosen?.profile?.id) }
    ForgetManageWhenAway(showsLibrary = chosen != null, picker = viewModel, manage = manage)
    // The picker stands in for the library, which leaves composition and with
    // it every saved place: the tab, the scroll. Held here per profile, so
    // "Stay as I am" comes back to them and another profile starts fresh.
    val places = rememberSaveableStateHolder()
    var lastChosen by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(chosen?.profile?.id) { chosen?.let { lastChosen = it.profile.id } }
    // A join — Switch household, or the owner's Use on this device — that
    // left the profile chosen here behind asks who is watching again, and
    // forgets the places it kept here. Heard here, not by the panel that
    // joined: the library, Settings with it, leaves the moment the profile is
    // gone, before the join has finished reading.
    LaunchedEffect(join, owner) {
        merge(join.joined, owner.joined).collect { kept ->
            if (kept) return@collect
            lastChosen?.let(places::removeState)
            viewModel.reopen()
        }
    }
    if (joining.needsJoin) {
        TvJoinHousehold(join)
        return
    }
    val cast = rememberLibraryPositions()
    if (chosen == null || cast.top != null) {
        val opened = rememberCastOpens(cast)
        if (cast.top != null) return TvCastOverPicker(cast, opened)
    }
    if (chosen != null) {
        places.SaveableStateProvider(chosen.profile.id) {
            content(TvChosenProfile(name = chosen.profile.name, onChoose = viewModel::reopen))
        }
        return
    }
    // Back on a reopened picker is "Stay as I am" — the phone's gate says
    // why. Registered first, so the PIN, Manage and the add flow, each with
    // a Back of its own composed after this one, take Back first while open.
    BackHandler(enabled = (state as? ProfileUiState.Picking)?.canStay == true, onBack = viewModel::stay)
    // What the remote left the picker from, and the row's scroll, kept here
    // while a PIN, the Profiles page or Manage stands in for the picker.
    var landing by remember { mutableStateOf<TvPickerSpot?>(null) }
    var profilesPage by remember { mutableStateOf(false) }
    var joiningElsewhere by remember { mutableStateOf(false) }
    val tiles = rememberLazyListState()
    val inManage = managing != ManageUiState.Closed
    val pickPrompt = pickPin
    // No grown-up here, the households not read yet: wait for them as for a sync round, Try again
    // reading them, never offering a first profile they may rule out. A failed read keeps its own Try again.
    val unread = (state as? ProfileUiState.Picking)?.takeIf { it.error == null && it.needsFirstProfile && !joining.read }?.copy(synced = false)
    when {
        inManage -> TvManageScreen(manage)
        pickPrompt != null -> TvPinPrompt(pickPrompt, onPin = viewModel::enterPin, onCancel = viewModel::cancelPin)
        profilesPage -> TvProfilesOverPicker(onManage = manage::open, onDone = { profilesPage = false })
        joiningElsewhere && joining.joinsElsewhere -> {
            BackHandler { joiningElsewhere = false }
            TvJoinHousehold(join) { joiningElsewhere = false }
        }
        else ->
            TvProfilePicker(
                state = unread ?: state,
                onChoose = { id ->
                    landing = TvPickerSpot.Tile(id)
                    viewModel.pick(id)
                },
                onStay = viewModel::stay,
                onRetry = if (unread != null) join::reread else viewModel::retry,
                onClaim = { id ->
                    landing = TvPickerSpot.Claim(id)
                    viewModel.claim(id)
                },
                onCreateFirst = viewModel::createFirst,
                onJoin = { joiningElsewhere = true }.takeIf { joining.joinsElsewhere },
                onManage = {
                    landing = TvPickerSpot.Manage
                    profilesPage = true
                },
                landing = landing,
                tiles = tiles,
            )
    }
}

/**
 * Manage, or the PIN it asks for in its place — one screen at a time, as
 * above — wherever it is opened: the Profiles page over the picker here, or
 * Settings' Profiles panel. [onClose] is its Done and its Back.
 */
@Composable
internal fun TvManageScreen(
    manage: ManageProfilesViewModel,
    onClose: () -> Unit = manage::close,
) {
    val state by manage.state.collectAsStateWithLifecycle()
    val prompt by manage.pin.collectAsStateWithLifecycle()
    val actions = remember(manage, onClose) { manage.manageActions(onClose) }
    prompt?.let { TvPinPrompt(it, onPin = manage::enterPin, onCancel = manage::cancelPin) } ?: TvManageProfiles(state, actions)
}

package ui.tv.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.profile.ProfileUiState
import catalog.profile.ProfileViewModel

/**
 * Who is watching, as the masthead needs it: the name its last entry shows,
 * and what selecting that entry does. The television twin of the phone's
 * `ui.ProfileBarState`, declared here rather than shared because that one
 * lives in `:ui-mobile`, which this module never sees.
 */
data class TvChosenProfile(
    val name: String,
    val onChoose: () -> Unit,
)

/**
 * The television counterpart to `ui.profile.ProfileGate`: the same
 * [ProfileViewModel] decides the same thing it decides on a phone — whether
 * a viewer has been chosen yet — and television asks it the same way,
 * showing [TvProfilePicker] in place of [content] until one has.
 *
 * Once one has, [content] is handed that viewer the way the phone's gate
 * hands its bar a `ProfileBarState`: the name, and [ProfileViewModel.reopen]
 * as what choosing it does — the same way back to the picker from the
 * masthead as the phone's bar offers, so switching viewer never needs a
 * second route.
 */
@Composable
internal fun TvProfileGate(content: @Composable (TvChosenProfile) -> Unit) {
    val viewModel: ProfileViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val chosen = state as? ProfileUiState.Chosen

    if (chosen == null) {
        TvProfilePicker(
            state = state,
            onChoose = viewModel::choose,
            onAdd = viewModel::add,
            onStay = viewModel::stay,
            onRetry = viewModel::retry,
            onRemove = viewModel::remove,
        )
        return
    }
    content(TvChosenProfile(name = chosen.profile.name, onChoose = viewModel::reopen))
}

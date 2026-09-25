package ui.tv.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.profile.ProfileUiState
import catalog.profile.ProfileViewModel

/**
 * The television counterpart to `ui.profile.ProfileGate`: the same
 * [ProfileViewModel] decides the same thing it decides on a phone — whether
 * a viewer has been chosen yet — and television asks it the same way,
 * showing [TvProfilePicker] in place of [content] until one has.
 *
 * Unlike the phone's gate, [content] takes no bar state here: television has
 * no bar yet to hand a reopen action to (the stub `TvApp` composes in its
 * place carries nothing to attach one to either). Whatever draws a real TV
 * bar decides what it needs from here, not this one guessing ahead of it.
 */
@Composable
internal fun TvProfileGate(content: @Composable () -> Unit) {
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
        )
        return
    }
    content()
}

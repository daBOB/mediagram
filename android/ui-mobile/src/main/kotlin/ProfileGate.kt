package ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.ProfileUiState
import catalog.ProfileViewModel

/**
 * Gates [content] on a chosen profile: a viewer, not the setup step the app
 * already answers, decides whose shelves these are, and nothing past this
 * point knows how to draw them without one.
 *
 * Shows [ProfilePickerScreen] in place of [content] while there is nobody
 * chosen yet, and hands [content] the bar state for whoever is once there is
 * — the name [ui.LibraryScaffold] shows, and what tapping it reopens.
 */
@Composable
internal fun ProfileGate(content: @Composable (ProfileBarState) -> Unit) {
    val viewModel: ProfileViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val chosen = state as? ProfileUiState.Chosen

    if (chosen == null) {
        ProfilePickerScreen(
            state = state,
            onChoose = viewModel::choose,
            onAdd = viewModel::add,
            onStay = viewModel::stay,
            onRemove = viewModel::remove,
        )
        return
    }
    content(ProfileBarState(name = chosen.profile.name, onChoose = viewModel::reopen))
}

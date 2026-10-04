package ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.profile.ProfileUiState
import catalog.profile.ProfileViewModel
import ui.ProfileBarState

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
        // Back on a reopened picker is "Stay as I am", as Escape is on the
        // web: the picker stands in for the library, so without this Back
        // would leave the app instead of returning to it. A first run has
        // nobody to return to, and Back there leaves as it always did.
        BackHandler(enabled = (state as? ProfileUiState.Picking)?.canStay == true, onBack = viewModel::stay)
        ProfilePickerScreen(
            state = state,
            onChoose = viewModel::choose,
            onAdd = viewModel::add,
            onStay = viewModel::stay,
            onRetry = viewModel::retry,
            onRemove = viewModel::remove,
        )
        return
    }
    content(ProfileBarState(name = chosen.profile.name, onChoose = viewModel::reopen))
}

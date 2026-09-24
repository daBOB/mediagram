package ui.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.first
import setup.login.LoginUiState
import setup.login.LoginViewModel

/**
 * Waits for [viewModel] to reach [LoginUiState.Authorized] and reports it
 * once — the same completion a second surface's sign-in step needs, kept
 * here so both ask the ViewModel the same question rather than each
 * watching its own copy of it.
 *
 * Entry is reconciled ahead of the wait: the Activity can retain Authorized
 * from a session that setup just found was signed out, and reconciling once
 * more here is what clears that before waiting on it again.
 */
@Composable
fun SignInCompletion(
    viewModel: LoginViewModel,
    onAuthorized: () -> Unit,
) {
    val authorized by rememberUpdatedState(onAuthorized)

    LaunchedEffect(viewModel) {
        viewModel.enterSignIn()
        viewModel.state.first { it is LoginUiState.Authorized }
        authorized()
    }
}

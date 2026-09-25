package ui.tv.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import setup.login.LoginStep
import setup.login.LoginUiState
import setup.login.LoginViewModel
import setup.login.promptFor
import ui.setup.SignInCompletion

/**
 * Sign-in on television: the same [LoginViewModel] `ui.MobileApp`'s private
 * `SignIn` composable drives, rendered one [TvTextQuestion] at a time
 * instead of the phone's single screen holding phone/code/password in
 * whichever field [setup.login.promptFor] names next.
 * [ui.setup.SignInCompletion] is reused as-is: both surfaces wait on the
 * same "is this authorized yet" rather than each watching its own copy of
 * it.
 */
@Composable
fun TvSignInScreen(onAuthorized: () -> Unit) {
    val viewModel: LoginViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    SignInCompletion(viewModel = viewModel, onAuthorized = onAuthorized)

    val step = promptFor(state) ?: return

    // Keyed on the step, not on the state class: a rejection of the code
    // leaves the step unchanged, so what was typed stays on screen to be
    // corrected instead of being cleared for a full retype — the same rule
    // `ui.setup.LoginScreen` follows.
    var input by remember(step) { mutableStateOf("") }
    val (label, onSubmit) =
        when (step) {
            LoginStep.PHONE -> "Phone number" to viewModel::submitPhone
            LoginStep.CODE -> "Login code" to viewModel::submitCode
            LoginStep.PASSWORD -> "Two-factor password" to viewModel::submitPassword
        }

    // No separate page heading here, unlike the application step: the
    // phone's own LoginScreen shows nothing above the field but a
    // rejection's message, and this mirrors exactly that — the field's own
    // label is the only other text either surface shows.
    TvTextQuestion(
        heading = "",
        explanation = (state as? LoginUiState.Failed)?.message,
        label = label,
        value = input,
        onValue = { input = it },
        onSubmit = { onSubmit(input) },
        secret = step == LoginStep.PASSWORD,
    )
}

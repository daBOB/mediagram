package login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import data.CoreProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uniffi.mediagram_core.AuthOutcome
import javax.inject.Inject

/**
 * Drives the sign-in steps. The login code and any two-factor password are
 * held only for the duration of the call that needs them and are never
 * logged; only the sign-in token, itself not a secret, survives between
 * [submitPhone] and [submitCode].
 *
 * A rejection keeps that token: the core deliberately hands the pending
 * login back on a wrong code and the password step back on a wrong
 * password, so [submitCode] and [submitPassword] stay callable straight
 * after one fails. Every failure therefore names the step it happened at,
 * so a surface can ask for that one credential again instead of restarting
 * the flow and spending a code request the person did not need.
 *
 * The core is awaited rather than injected: it is built from credentials
 * read out of encrypted storage, which on a first run have only just been
 * typed in. Whether a session already exists is therefore answered a beat
 * after construction, not during it.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val coreProvider: CoreProvider,
) : ViewModel() {

    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.NeedsPhone)
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private var signInToken: String? = null

    init {
        viewModelScope.launch {
            if (coreProvider.awaitCore().isAuthorized()) _state.value = LoginUiState.Authorized
        }
    }

    fun submitPhone(phone: String) {
        viewModelScope.launch {
            runCatching { coreProvider.awaitCore().requestCode(phone) }
                .onSuccess {
                    signInToken = it
                    _state.value = LoginUiState.NeedsCode
                }
                .onFailure { _state.value = it.failedAt(LoginStep.PHONE) }
        }
    }

    fun submitCode(code: String) {
        val token = signInToken ?: return
        viewModelScope.launch {
            runCatching { coreProvider.awaitCore().signIn(token, code) }
                .onSuccess { outcome ->
                    _state.value = when (outcome) {
                        AuthOutcome.DONE -> LoginUiState.Authorized
                        AuthOutcome.PASSWORD_NEEDED -> LoginUiState.NeedsPassword
                    }
                }
                .onFailure { _state.value = it.failedAt(LoginStep.CODE) }
        }
    }

    fun submitPassword(password: String) {
        viewModelScope.launch {
            runCatching { coreProvider.awaitCore().checkPassword(password) }
                .onSuccess { _state.value = LoginUiState.Authorized }
                .onFailure { _state.value = it.failedAt(LoginStep.PASSWORD) }
        }
    }
}

private fun Throwable.failedAt(step: LoginStep): LoginUiState.Failed =
    LoginUiState.Failed(step, message ?: "Sign-in failed")

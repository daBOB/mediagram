package login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import data.CoreClient
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
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val core: CoreClient,
) : ViewModel() {

    private val _state = MutableStateFlow(
        if (core.isAuthorized()) LoginUiState.Authorized else LoginUiState.NeedsPhone,
    )
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private var signInToken: String? = null

    fun submitPhone(phone: String) {
        viewModelScope.launch {
            runCatching { core.requestCode(phone) }
                .onSuccess {
                    signInToken = it
                    _state.value = LoginUiState.NeedsCode
                }
                .onFailure { _state.value = it.toFailedState() }
        }
    }

    fun submitCode(code: String) {
        val token = signInToken ?: return
        viewModelScope.launch {
            runCatching { core.signIn(token, code) }
                .onSuccess { outcome ->
                    _state.value = when (outcome) {
                        AuthOutcome.DONE -> LoginUiState.Authorized
                        AuthOutcome.PASSWORD_NEEDED -> LoginUiState.NeedsPassword
                    }
                }
                .onFailure { _state.value = it.toFailedState() }
        }
    }

    fun submitPassword(password: String) {
        viewModelScope.launch {
            runCatching { core.checkPassword(password) }
                .onSuccess { _state.value = LoginUiState.Authorized }
                .onFailure { _state.value = it.toFailedState() }
        }
    }
}

private fun Throwable.toFailedState(): LoginUiState.Failed =
    LoginUiState.Failed(message ?: "Sign-in failed")

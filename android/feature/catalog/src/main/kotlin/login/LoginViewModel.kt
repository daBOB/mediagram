package login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import data.CoreProvider
import data.coreSentence
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
 *
 * And answered again whenever the core is replaced. This ViewModel outlives
 * the screen that shows it — it belongs to the Activity — so after a
 * start-over it would otherwise still be reporting the sign-in that was
 * just undone, and the sign-in step would compose with nothing left to ask
 * for and render an empty screen. Following the core rather than being told
 * about the reset keeps that answer derived from the same thing every other
 * part of setup is derived from.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val coreProvider: CoreProvider,
    private val dispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.NeedsPhone)
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private var signInToken: String? = null

    init {
        viewModelScope.launch {
            coreProvider.core.collect { core ->
                // A token belongs to the core that issued it; a new core
                // cannot redeem it, and keeping one would let submitCode
                // spend a login attempt that was never going to work.
                signInToken = null
                val authorized = core != null && withContext(dispatcher) { core.isAuthorized() }
                _state.value = if (authorized) LoginUiState.Authorized else LoginUiState.NeedsPhone
            }
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

/**
 * The core writes its refusals to be read — "that code was not accepted" —
 * and the generated exception's own message renders as `v1=` and the field.
 * The sentence inside it is the one thing worth putting on screen.
 */
private fun Throwable.failedAt(step: LoginStep): LoginUiState.Failed =
    LoginUiState.Failed(step, coreSentence() ?: message ?: "Sign-in failed")

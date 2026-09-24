package setup.login

/**
 * Which credential the sign-in flow is asking for. A rejection names the
 * step it happened at, because the three are not equally expensive to redo:
 * a wrong code or a wrong password can be retyped against the login attempt
 * already in flight, while starting over from the phone number spends a
 * fresh code request — and repeated code requests are what earn a flood
 * wait.
 */
enum class LoginStep { PHONE, CODE, PASSWORD }

/** What the login screen renders; the television surface renders the same states. */
sealed interface LoginUiState {
    data object NeedsPhone : LoginUiState

    data object NeedsCode : LoginUiState

    data object NeedsPassword : LoginUiState

    data object Authorized : LoginUiState

    /**
     * [step] is the one to ask for again, not necessarily the first one.
     * The core hands the pending login token back on a wrong code and the
     * password token back on a wrong password, so both steps can be
     * retried directly; only a failure to request a code at all leaves
     * nothing to retry and sends the person back to the phone number.
     */
    data class Failed(
        val step: LoginStep,
        val message: String,
    ) : LoginUiState
}

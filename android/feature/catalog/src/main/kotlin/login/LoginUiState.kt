package login

/** What the login screen renders; the television surface renders the same states. */
sealed interface LoginUiState {
    data object NeedsPhone : LoginUiState
    data object NeedsCode : LoginUiState
    data object NeedsPassword : LoginUiState
    data object Authorized : LoginUiState
    data class Failed(val message: String) : LoginUiState
}

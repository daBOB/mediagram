package setup.login

/**
 * The credential this state asks for, or `null` once there is nothing left
 * to ask. A rejection re-asks for the step it names rather than the first
 * one: the core keeps the login and password tokens alive across a wrong
 * entry, so a retype costs nothing, where dropping back to the phone field
 * would spend a fresh code request and move the account towards a flood
 * wait.
 */
fun promptFor(state: LoginUiState): LoginStep? =
    when (state) {
        LoginUiState.NeedsPhone -> LoginStep.PHONE
        LoginUiState.NeedsCode -> LoginStep.CODE
        LoginUiState.NeedsPassword -> LoginStep.PASSWORD
        is LoginUiState.Failed -> state.step
        LoginUiState.Authorized -> null
    }

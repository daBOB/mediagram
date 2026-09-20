package ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import designsystem.Spacing
import login.LoginStep
import login.LoginUiState

/**
 * The credential this state asks for, or `null` once there is nothing left
 * to ask. A rejection re-asks for the step it names rather than the first
 * one: the core keeps the login and password tokens alive across a wrong
 * entry, so a retype costs nothing, where dropping back to the phone field
 * would spend a fresh code request and move the account towards a flood
 * wait.
 */
internal fun promptFor(state: LoginUiState): LoginStep? = when (state) {
    LoginUiState.NeedsPhone -> LoginStep.PHONE
    LoginUiState.NeedsCode -> LoginStep.CODE
    LoginUiState.NeedsPassword -> LoginStep.PASSWORD
    is LoginUiState.Failed -> state.step
    LoginUiState.Authorized -> null
}

@Composable
fun LoginScreen(
    state: LoginUiState,
    onSubmitPhone: (String) -> Unit,
    onSubmitCode: (String) -> Unit,
    onSubmitPassword: (String) -> Unit,
) {
    val step = promptFor(state) ?: return

    // Keyed on the step, not on the state class: a rejection of the code
    // leaves the step unchanged, so what was typed stays on screen to be
    // corrected instead of being cleared for a full retype.
    var input by remember(step) { mutableStateOf("") }
    val (label, onSubmit) = when (step) {
        LoginStep.PHONE -> "Phone number" to onSubmitPhone
        LoginStep.CODE -> "Login code" to onSubmitCode
        LoginStep.PASSWORD -> "Two-factor password" to onSubmitPassword
    }
    val isSecret = step == LoginStep.PASSWORD

    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state is LoginUiState.Failed) {
            Text(text = state.message)
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text(label) },
            visualTransformation = if (isSecret) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            // Masking only hides the glyphs from whoever is looking at the
            // screen. KeyboardType.Password is what tells the IME this is a
            // secret, so it stops offering predictions, stops learning the
            // word, and — for a keyboard that syncs its dictionary — stops
            // the password leaving the device altogether.
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isSecret) KeyboardType.Password else KeyboardType.Text,
            ),
        )
        Button(onClick = { onSubmit(input) }) { Text("Continue") }
    }
}

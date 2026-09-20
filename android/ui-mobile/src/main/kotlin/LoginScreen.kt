package ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import designsystem.Spacing
import login.LoginUiState

@Composable
fun LoginScreen(
    state: LoginUiState,
    onSubmitPhone: (String) -> Unit,
    onSubmitCode: (String) -> Unit,
    onSubmitPassword: (String) -> Unit,
) {
    if (state is LoginUiState.Authorized) return

    var input by remember(state::class) { mutableStateOf("") }
    val (label, onSubmit) = when (state) {
        LoginUiState.NeedsPhone, is LoginUiState.Failed -> "Phone number" to onSubmitPhone
        LoginUiState.NeedsCode -> "Login code" to onSubmitCode
        LoginUiState.NeedsPassword -> "Two-factor password" to onSubmitPassword
        LoginUiState.Authorized -> return
    }

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
            visualTransformation = if (state == LoginUiState.NeedsPassword) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
        )
        Button(onClick = { onSubmit(input) }) { Text("Continue") }
    }
}

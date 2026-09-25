package ui.tv.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

private const val APPLICATION_HEADING = "Connect this device to Telegram"

private const val APPLICATION_EXPLANATION =
    "Sign in at my.telegram.org, open “API development tools”, and copy the " +
        "api_id and api_hash it shows for your application."

private enum class ApplicationStep { API_ID, API_HASH }

/**
 * Step one on television: the same two Telegram application values
 * `ui.setup.TelegramApplicationScreen` asks for on one screen, asked here
 * across two — [TvTextQuestion] is one question per screen, the same shape
 * sign-in's phone/code/password steps use, and a remote has nowhere to land
 * on a second field once the first already has its focus.
 *
 * [error] does not say which of the two typed values Telegram rejected —
 * `setup.SetupUiState.NeedsApplication` carries one message for both — so an
 * error sends the remote back to api_id rather than guessing which field to
 * reopen. Both values stay in hand across that return: a rejection is a
 * reason to re-check what was typed, not to retype it from nothing.
 */
@Composable
fun TvApplicationScreen(
    error: String?,
    onSubmit: (apiId: String, apiHash: String) -> Unit,
) {
    var step by remember { mutableStateOf(ApplicationStep.API_ID) }
    var apiId by remember { mutableStateOf("") }
    var apiHash by remember { mutableStateOf("") }

    LaunchedEffect(error) { if (error != null) step = ApplicationStep.API_ID }

    when (step) {
        ApplicationStep.API_ID ->
            TvTextQuestion(
                prompt = applicationPrompt(error, "api_id"),
                value = apiId,
                onValue = { apiId = it },
                onSubmit = { if (apiId.isNotBlank()) step = ApplicationStep.API_HASH },
            )

        ApplicationStep.API_HASH ->
            TvTextQuestion(
                prompt = "api_hash",
                value = apiHash,
                onValue = { apiHash = it },
                onSubmit = { if (apiHash.isNotBlank()) onSubmit(apiId, apiHash) },
                secret = true,
            )
    }
}

private fun applicationPrompt(
    error: String?,
    field: String,
): String = listOfNotNull(error, APPLICATION_HEADING, APPLICATION_EXPLANATION, field).joinToString("\n\n")

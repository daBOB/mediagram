package ui.tv.setup

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType

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
 * on a second field once the first already has its focus. Heading and
 * explanation are the phone's own words, unabridged, on both steps: a
 * person moving from api_id to api_hash should not have to remember where
 * my.telegram.org's page went.
 *
 * [error] does not say which of the two typed values Telegram rejected —
 * `setup.SetupUiState.NeedsApplication` carries one message for both — so an
 * error sends the remote back to api_id rather than guessing which field to
 * reopen. Both values stay in hand across that return: a rejection is a
 * reason to re-check what was typed, not to retype it from nothing.
 *
 * Back from api_hash returns to api_id instead of leaving the app — the
 * phone has both fields on one screen with nowhere for Back to go; TV's
 * own two-screen split invents exactly one place Back needs to reach that
 * the phone never had to.
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
                heading = APPLICATION_HEADING,
                explanation = applicationExplanation(error),
                label = "api_id",
                value = apiId,
                onValue = { apiId = it },
                onSubmit = { if (apiId.isNotBlank()) step = ApplicationStep.API_HASH },
                keyboardType = KeyboardType.Number,
            )

        ApplicationStep.API_HASH -> {
            BackHandler { step = ApplicationStep.API_ID }
            TvTextQuestion(
                heading = APPLICATION_HEADING,
                explanation = APPLICATION_EXPLANATION,
                label = "api_hash",
                value = apiHash,
                onValue = { apiHash = it },
                onSubmit = { if (apiHash.isNotBlank()) onSubmit(apiId, apiHash) },
                secret = true,
            )
        }
    }
}

private fun applicationExplanation(error: String?): String = listOfNotNull(error, APPLICATION_EXPLANATION).joinToString("\n\n")

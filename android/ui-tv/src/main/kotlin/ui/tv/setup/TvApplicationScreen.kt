package ui.tv.setup

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import setup.API_HASH_ERROR

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
 * [error] does say which of the two typed values Telegram rejected —
 * `setup.API_ID_ERROR` and [API_HASH_ERROR] are two different sentences —
 * so a hash rejection reopens api_hash instead of every error bouncing the
 * remote back to api_id regardless of which field it was actually about.
 * Both values stay in hand across that return: a rejection is a reason to
 * re-check what was typed, not to retype it from nothing.
 *
 * Back from api_hash returns to api_id instead of leaving the app — the
 * phone has both fields on one screen with nowhere for Back to go; TV's
 * own two-screen split invents exactly one place Back needs to reach that
 * the phone never had to.
 *
 * [initialApiId] is the one already on record when Settings asks again, as
 * the phone's form fills it in: an api_id is not a secret, and changing
 * only the hash should not mean retyping it. The hash always starts blank.
 */
@Composable
fun TvApplicationScreen(
    error: String?,
    onSubmit: (apiId: String, apiHash: String) -> Unit,
    initialApiId: String = "",
) {
    var step by remember { mutableStateOf(ApplicationStep.API_ID) }
    var apiId by remember { mutableStateOf(initialApiId) }
    var apiHash by remember { mutableStateOf("") }

    LaunchedEffect(error) {
        if (error != null) {
            step = if (error == API_HASH_ERROR) ApplicationStep.API_HASH else ApplicationStep.API_ID
        }
    }

    when (step) {
        ApplicationStep.API_ID ->
            TvTextQuestion(
                heading = APPLICATION_HEADING,
                explanation = APPLICATION_EXPLANATION,
                label = "api_id",
                value = apiId,
                onValue = { apiId = it },
                onSubmit = { if (apiId.isNotBlank()) step = ApplicationStep.API_HASH },
                keyboardType = KeyboardType.Number,
                error = error?.takeIf { it != API_HASH_ERROR },
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
                error = error?.takeIf { it == API_HASH_ERROR },
            )
        }
    }
}

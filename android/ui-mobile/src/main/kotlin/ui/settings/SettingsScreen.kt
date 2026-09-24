package ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import designsystem.Spacing
import setup.SettingsUiState
import setup.SettingsViewModel
import ui.components.Block
import ui.setup.LibraryScreen
import ui.setup.TelegramApplicationScreen

/** A part of Settings that takes the whole screen while it is open. */
private enum class SettingsPanel { Library, Application }

/**
 * Settings: the Telegram connection — who, which library, which datacentre,
 * and changing any of it — then the disk cache.
 *
 * Changing the library and the application identity reuse the very screens
 * setup asks them on, laid over this one: the questions are the same and so
 * are the answers' rules, so they are asked the same way. What signing out
 * and a changed library lead to is [SettingsOutcomes]' to act on.
 */
@Composable
internal fun SettingsScreen(cache: @Composable () -> Unit) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var panel by rememberSaveable { mutableStateOf<SettingsPanel?>(null) }
    var openedBeforeAction by rememberSaveable { mutableStateOf(state.completedActionId) }
    var askingSignOut by remember { mutableStateOf(false) }

    // Read afresh on every visit: the ViewModel outlives this screen, and a
    // row read before a sign-out and a new sign-in would name the old account.
    LaunchedEffect(Unit) { viewModel.refresh() }

    // Independent of the library's acknowledgement: both surfaces must see
    // success, even when this form was detached when the action finished.
    LaunchedEffect(state.completedActionId) {
        if (state.completedActionId > openedBeforeAction) panel = null
    }

    when (panel) {
        SettingsPanel.Library -> {
            BackHandler { panel = null }
            LibraryScreen(
                choices = state.choices,
                error = state.notice,
                // One install at a time; a second tap waits for the first.
                onChoose = { handle -> if (!state.busy) viewModel.chooseLibrary(handle) },
                onLookAgain = viewModel::listLibraries,
            )
        }

        SettingsPanel.Application -> {
            BackHandler { panel = null }
            if (state.profileReloadNeeded) {
                ProfileReload(state, viewModel::retryProfiles)
            } else {
                TelegramApplicationScreen(
                    error = state.notice,
                    onSubmit = viewModel::changeApplication,
                    initialApiId = state.apiId?.toString().orEmpty(),
                )
            }
        }

        null -> {
            SettingsRows(
                state = state,
                onChangeLibrary = {
                    viewModel.clearNotice()
                    openedBeforeAction = state.completedActionId
                    panel = SettingsPanel.Library
                    viewModel.listLibraries()
                },
                onChangeApplication = {
                    viewModel.clearNotice()
                    openedBeforeAction = state.completedActionId
                    panel = SettingsPanel.Application
                },
                onSignOut = { askingSignOut = true },
                onRetryProfiles = viewModel::retryProfiles,
                cache = cache,
            )
        }
    }

    SignOutConfirmation(
        asking = askingSignOut,
        onDismiss = { askingSignOut = false },
        onConfirm = viewModel::signOut,
    )
}

@Composable
private fun SettingsRows(
    state: SettingsUiState,
    onChangeLibrary: () -> Unit,
    onChangeApplication: () -> Unit,
    onSignOut: () -> Unit,
    onRetryProfiles: () -> Unit,
    cache: @Composable () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
        item { Block(heading = "Telegram", rows = telegramRows(state)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                // Waiting on an answer greys the actions rather than hiding
                // them: what is on offer does not change, only when.
                OutlinedButton(onClick = onChangeLibrary, enabled = !state.busy) { Text("Change library") }
                OutlinedButton(onClick = onChangeApplication, enabled = !state.busy) {
                    Text("Application id and hash…")
                }
                OutlinedButton(onClick = onSignOut, enabled = !state.busy) { Text("Sign out") }
                if (state.profileReloadNeeded) {
                    ProfileReload(state, onRetryProfiles)
                } else {
                    state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        item { cache() }
    }
}

@Composable
private fun ProfileReload(
    state: SettingsUiState,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text(if (state.busy) "Loading profiles…" else "Reload profiles to continue.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onRetry, enabled = !state.busy) { Text("Try again") }
    }
}

/** The Telegram block's rows, pure so a test can pin them. "…" is a row still being asked. */
internal fun telegramRows(state: SettingsUiState): List<Pair<String, String?>> =
    listOf(
        "Account" to (state.account ?: "…"),
        "Library" to (state.library ?: "…"),
        "Datacenter" to (state.datacenter ?: "…"),
        "Session" to (state.connection ?: "…"),
    )

/**
 * Asked before signing out, and worded against start over's: this ends the
 * login and takes the account's catalog with it, but keeps what is the
 * device's own, so signing back in is all that is left to do.
 */
@Composable
private fun SignOutConfirmation(
    asking: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!asking) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign out?") },
        text = {
            Text(
                "This signs this device out of Telegram and removes the library it " +
                    "was reading. The api_id, api_hash and TMDB key stay; signing in " +
                    "again is all it takes to come back.",
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text("Sign out") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

package ui

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
import setup.SettingsEvent
import setup.SettingsUiState
import setup.SettingsViewModel

/** A part of Settings that takes the whole screen while it is open. */
private enum class SettingsPanel { Library, Application }

/**
 * Settings: the Telegram connection — who, which library, which datacentre,
 * and changing any of it — then the disk cache.
 *
 * Changing the library and the application identity reuse the very screens
 * setup asks them on, laid over this one: the questions are the same and so
 * are the answers' rules, so they are asked the same way. [onLibraryChanged]
 * has the shelves read the new library; [onSignedOut] hands back to setup,
 * which finds no login and asks for one.
 */
@Composable
internal fun SettingsScreen(
    onLibraryChanged: () -> Unit,
    onSignedOut: () -> Unit,
    cache: @Composable () -> Unit,
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var panel by rememberSaveable { mutableStateOf<SettingsPanel?>(null) }
    var askingSignOut by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SettingsEvent.LibraryChanged -> { panel = null; onLibraryChanged() }
                SettingsEvent.ApplicationChanged -> panel = null
                SettingsEvent.SignedOut -> onSignedOut()
            }
        }
    }

    when (panel) {
        SettingsPanel.Library -> {
            BackHandler { panel = null }
            LibraryScreen(
                choices = state.choices,
                error = state.notice,
                onChoose = viewModel::chooseLibrary,
                onLookAgain = viewModel::listLibraries,
            )
        }
        SettingsPanel.Application -> {
            BackHandler { panel = null }
            TelegramApplicationScreen(
                error = state.notice,
                onSubmit = viewModel::changeApplication,
                initialApiId = state.apiId?.toString().orEmpty(),
            )
        }
        null -> SettingsRows(
            state = state,
            onChangeLibrary = { panel = SettingsPanel.Library; viewModel.listLibraries() },
            onChangeApplication = { panel = SettingsPanel.Application },
            onSignOut = { askingSignOut = true },
            cache = cache,
        )
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
                state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
        item { cache() }
    }
}

/** The Telegram block's rows, pure so a test can pin them. "…" is a row still being asked. */
internal fun telegramRows(state: SettingsUiState): List<Pair<String, String?>> = listOf(
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
private fun SignOutConfirmation(asking: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
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
            TextButton(onClick = { onDismiss(); onConfirm() }) { Text("Sign out") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

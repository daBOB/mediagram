package ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import designsystem.Spacing
import setup.SettingsUiState
import setup.telegramRows
import ui.components.Block

/**
 * Settings › Telegram: the Connection ledger and the actions that change
 * it, beside the account's Active sessions — the approved mockup's own two
 * columns (`round2/b-telegram.html`). Same [SettingsUiState] every screen
 * before this one read; only the layout is new.
 */
@Composable
internal fun TelegramSection(
    expanded: Boolean,
    state: SettingsUiState,
    onChangeLibrary: () -> Unit,
    onChangeApplication: () -> Unit,
    onSignOut: () -> Unit,
    onLoadSessions: () -> Unit,
    onRevokeSession: (String) -> Unit,
) {
    SettingsColumns(
        expanded = expanded,
        columns =
            listOf(
                { ConnectionColumn(state, onChangeLibrary, onChangeApplication, onSignOut) },
                { SessionsSection(sessions = state.sessions, error = state.sessionsError, onLoad = onLoadSessions, onRevoke = onRevokeSession) },
            ),
    )
}

@Composable
private fun ConnectionColumn(
    state: SettingsUiState,
    onChangeLibrary: () -> Unit,
    onChangeApplication: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column {
        Block(heading = "Connection", rows = telegramRows(state))
        Row(modifier = Modifier.padding(top = Spacing.large), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LinePill(text = "Change library", onClick = onChangeLibrary, enabled = !state.busy)
            LinePill(text = "Application id and hash…", onClick = onChangeApplication, enabled = !state.busy)
        }
        state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Spacing.small)) }
        Row(modifier = Modifier.padding(top = 32.dp)) {
            QuietPill(text = "Sign out", onClick = onSignOut, enabled = !state.busy)
        }
    }
}

/** The panel's own reload prompt — [ui.settings.SettingsScreen] shows this in place of [ui.setup.TelegramApplicationScreen] once a replaced identity is waiting on its profiles. */
@Composable
internal fun ProfileReload(
    state: SettingsUiState,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small), modifier = Modifier.padding(Spacing.large)) {
        state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text(if (state.busy) "Loading profiles…" else "Reload profiles to continue.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onRetry, enabled = !state.busy) { Text("Try again") }
    }
}

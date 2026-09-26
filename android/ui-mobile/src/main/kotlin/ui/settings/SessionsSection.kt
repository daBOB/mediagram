package ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import designsystem.Spacing
import uniffi.mediagram_core.SessionSummary

/**
 * This app's active sessions, plus the current one — the same rows the web
 * Settings page shows, confirmed in place rather than with a dialog: a
 * second tap on "Sign out" is what actually revokes it.
 */
@Composable
internal fun SessionsSection(
    sessions: List<SessionSummary>?,
    error: String?,
    onLoad: () -> Unit,
    onRevoke: (String) -> Unit,
) {
    LaunchedEffect(Unit) { onLoad() }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text("Active sessions", style = MaterialTheme.typography.titleMedium)
        when {
            error != null -> Text(error, style = MaterialTheme.typography.bodySmall)
            sessions == null -> Text("Reading the account's sessions…", style = MaterialTheme.typography.bodySmall)
            sessions.isEmpty() -> Text("No sessions.", style = MaterialTheme.typography.bodySmall)
            else -> for (session in sessions) SessionRow(session, onRevoke)
        }
    }
}

@Composable
private fun SessionRow(
    session: SessionSummary,
    onRevoke: (String) -> Unit,
) {
    var confirming by remember(session.id) { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(
                session.device + if (session.current) " (this device)" else "",
                style = MaterialTheme.typography.bodyMedium,
            )
            val detail = listOfNotNull(session.platform, session.app, session.location).joinToString(" · ")
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
        if (!session.current) {
            TextButton(onClick = { if (confirming) onRevoke(session.id) else confirming = true }) {
                Text(if (confirming) "Confirm sign out" else "Sign out")
            }
        }
    }
}

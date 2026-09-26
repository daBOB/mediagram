package ui.tv.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import designsystem.Spacing
import ui.tv.TvTextRow
import ui.tv.catalog.TvQuietLine
import uniffi.mediagram_core.SessionSummary

/**
 * The phone's active sessions on a television: every session on the
 * account, this device's marked and never offered for sign-out. Confirmed
 * in place as the phone does — the first press on a session's row turns it
 * into "Confirm sign out", and only the second revokes it.
 */
@Composable
internal fun TvSessionsBlock(
    sessions: List<SessionSummary>?,
    error: String?,
    onLoad: () -> Unit,
    onRevoke: (String) -> Unit,
) {
    LaunchedEffect(Unit) { onLoad() }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        TvInfoBlock(heading = "Active sessions", rows = emptyList())
        when {
            error != null -> TvQuietLine(error)
            sessions == null -> TvQuietLine("Reading the account's sessions…")
            sessions.isEmpty() -> TvQuietLine("No sessions.")
            else -> for (session in sessions) TvSessionRow(session, onRevoke)
        }
    }
}

@Composable
private fun TvSessionRow(
    session: SessionSummary,
    onRevoke: (String) -> Unit,
) {
    var confirming by remember(session.id) { mutableStateOf(false) }
    Column {
        if (session.current) {
            TvQuietLine("${session.device} (this device)")
        } else {
            TvTextRow(
                text = if (confirming) "Confirm sign out — ${session.device}" else "Sign out ${session.device}",
                onClick = { if (confirming) onRevoke(session.id) else confirming = true },
            )
        }
        TvQuietLine(listOfNotNull(session.platform, session.app, session.location).joinToString(" · "))
    }
}

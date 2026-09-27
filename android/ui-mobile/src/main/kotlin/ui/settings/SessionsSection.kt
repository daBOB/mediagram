package ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import designsystem.LocalCatalogueTones
import designsystem.SectionHead
import uniffi.mediagram_core.SessionSummary

/**
 * This app's active sessions, plus the current one — the same rows the web
 * Settings page shows, confirmed in place rather than with a dialog: a
 * second tap on "Sign out" is what actually revokes it. No per-session
 * device glyph: [SessionSummary] carries only free-text fields, and the
 * approved mockup's own icons key off a distinction this app cannot draw
 * (a deliberate gap, not an oversight).
 */
@Composable
internal fun SessionsSection(
    sessions: List<SessionSummary>?,
    error: String?,
    onLoad: () -> Unit,
    onRevoke: (String) -> Unit,
) {
    LaunchedEffect(Unit) { onLoad() }
    val tones = LocalCatalogueTones.current
    Column {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "Active sessions", style = SectionHead, color = MaterialTheme.colorScheme.onSurface)
            sessions?.let {
                Text(text = "${it.size}", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp), color = tones.quiet)
            }
        }
        Spacer(Modifier.height(12.dp))
        when {
            error != null -> Text(error, style = MaterialTheme.typography.bodySmall)
            sessions == null -> Text("Reading the account's sessions…", style = MaterialTheme.typography.bodySmall)
            sessions.isEmpty() -> Text("No sessions.", style = MaterialTheme.typography.bodySmall)
            else ->
                Column {
                    for (session in sessions) {
                        SessionRow(session, onRevoke)
                        HorizontalDivider(color = tones.ruleSoft)
                    }
                }
        }
    }
}

@Composable
private fun SessionRow(
    session: SessionSummary,
    onRevoke: (String) -> Unit,
) {
    var confirming by remember(session.id) { mutableStateOf(false) }
    val tones = LocalCatalogueTones.current
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = session.device, style = MaterialTheme.typography.bodyMedium)
            val detail = listOfNotNull(session.platform, session.app, session.location).joinToString(" · ")
            Text(text = detail, style = MaterialTheme.typography.bodySmall, color = tones.quiet)
        }
        if (session.current) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
                Text(
                    text = "This device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        } else {
            QuietPill(
                text = if (confirming) "Confirm sign out" else "Sign out",
                onClick = { if (confirming) onRevoke(session.id) else confirming = true },
                small = true,
            )
        }
    }
}

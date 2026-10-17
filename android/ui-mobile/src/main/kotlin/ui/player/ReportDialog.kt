package ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import designsystem.Spacing
import model.REPORT_NOTE_MAX
import model.ReportReason
import model.ReportWords
import player.ReportStage
import player.ReportUi
import ui.chrome.CatalogueTextButton
import ui.chrome.IndicationInk
import ui.chrome.NoDialogAnimation

/**
 * The broken-video report, as the web's sheet: a reason, an optional note, Send.
 * Opens on the reason a failure presets, otherwise on none; Send waits for a
 * reason and for any send in flight. A failed send keeps the sheet open.
 */
@Composable
internal fun ReportDialog(ui: ReportUi, onSend: (ReportReason, String) -> Unit, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf(ui.preset) }
    var note by remember { mutableStateOf("") }

    // Opened from over the video: the dialog is the page's own surface, so its buttons tint in the page's ink.
    IndicationInk(MaterialTheme.colorScheme.onSurface) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                NoDialogAnimation()
                Text(ReportWords.HEADING)
            },
            text = {
                Column {
                    Column(Modifier.selectableGroup()) {
                        ReportReason.entries.forEach { entry ->
                            Row(
                                modifier =
                                    Modifier.fillMaxWidth().selectable(selected = reason == entry, role = Role.RadioButton) { reason = entry },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = reason == entry, onClick = null, modifier = Modifier.padding(end = Spacing.small))
                                Text(entry.words)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(REPORT_NOTE_MAX) },
                        singleLine = true,
                        label = { Text(ReportWords.NOTE) },
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.medium),
                    )
                    if (ui.stage == ReportStage.FAILED) {
                        Text(ReportWords.FAILED, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = Spacing.small))
                    }
                }
            },
            confirmButton = {
                CatalogueTextButton(
                    onClick = { reason?.let { onSend(it, note) } },
                    enabled = reason != null && ui.stage != ReportStage.SENDING,
                ) { Text(ReportWords.SEND) }
            },
            dismissButton = { CatalogueTextButton(onClick = onDismiss) { Text(ReportWords.CANCEL) } },
        )
    }
}

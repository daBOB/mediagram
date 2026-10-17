package ui.tv.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import designsystem.Spacing
import designsystem.TvType
import model.REPORT_NOTE_MAX
import model.ReportReason
import model.ReportWords
import player.PlayerViewModel
import player.ReportStage
import player.ReportUi
import ui.tv.setup.LocalTvDialogKeys
import ui.tv.setup.TvDialog
import ui.tv.setup.TvTextQuestion

/**
 * The broken-video report over the film — the phone's `ReportDialog` as a
 * television dialog: the reasons as rows OK picks one of, a note, Cancel
 * and Send. It opens on the reason a failure presets, otherwise on none,
 * with the remote on the first reason; Send waits for a reason and for any
 * send in flight. A failed send keeps it open and says so under the note.
 * Back is Cancel.
 *
 * The note is a row, not a field: a field brings the keyboard up the moment
 * the remote passes over it, and on a television the keyboard covers half
 * the screen. OK on the row asks for the note in a window of its own, as
 * "＋ New list" asks for a list's name, and the remote comes back to the row.
 */
@Composable
internal fun TvReportDialog(
    ui: ReportUi,
    onSend: (ReportReason, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by rememberSaveable { mutableStateOf(ui.preset) }
    var note by rememberSaveable { mutableStateOf("") }
    var writing by rememberSaveable { mutableStateOf(false) }
    var wrote by rememberSaveable { mutableStateOf(false) }
    if (writing) {
        val done = {
            writing = false
            wrote = true
        }
        TvQuestionWindow(onDismiss = done) {
            TvTextQuestion(heading = "", explanation = null, label = ReportWords.NOTE, value = note, onValue = { note = it.take(REPORT_NOTE_MAX) }, onSubmit = done)
        }
        return
    }

    val first = remember { FocusRequester() }
    val noteRow = remember { FocusRequester() }
    TvDialog(
        title = ReportWords.HEADING,
        body = "",
        onDismissRequest = onDismiss,
        initialFocus = if (wrote) noteRow else first,
        content = {
            Column(Modifier.padding(top = Spacing.medium)) {
                ReportReason.entries.forEach { entry ->
                    TvChoiceRow(
                        label = entry.words,
                        selected = entry == reason,
                        onClick = { reason = entry },
                        focusRequester = first.takeIf { entry == ReportReason.entries.first() },
                    )
                }
            }
            TvOverlaySurface(
                onClick = { writing = true },
                enabled = true,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.small).focusRequester(noteRow),
            ) {
                Text(
                    text = note.ifEmpty { ReportWords.NOTE },
                    style = TvPlayerType.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.extraSmall),
                )
            }
            if (ui.stage == ReportStage.FAILED) {
                Text(ReportWords.FAILED, style = TvType.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = Spacing.small))
            }
        },
    ) {
        Button(onClick = onDismiss) { Text(ReportWords.CANCEL, style = TvType.labelLarge) }
        Button(onClick = { reason?.let { onSend(it, note) } }, enabled = reason != null && ui.stage != ReportStage.SENDING) {
            Text(ReportWords.SEND, style = TvType.labelLarge)
        }
    }
}

/** The report over the film while it is open; its window's keys go to the player first, as the list dialog's do. */
@Composable
internal fun TvReportOverPlayer(
    report: ReportUi,
    viewModel: PlayerViewModel,
    keys: (KeyEvent) -> Boolean,
) {
    if (!report.open) return
    CompositionLocalProvider(LocalTvDialogKeys provides keys) {
        TvReportDialog(ui = report, onSend = viewModel::sendReport, onDismiss = viewModel::closeReport)
    }
}

package ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import ui.chrome.CatalogueTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import designsystem.Spacing
import model.ReportWords
import player.PlayerUiState
import player.PlayerViewModel
import player.ReportStage
import player.retry
import player.ReportUi

/**
 * A failed set's message, and Retry — the phone's touch equivalent of the
 * web's seek-to-retry (there is no button there; a viewer drags the scrub
 * bar). A phone's failures are almost always the network rather than a
 * broken file, so re-opening the same set at wherever it was last saved to
 * is usually enough; recorded as a deliberate, phone-only addition rather
 * than web debt. The top bar is gone while a title has failed, so Report
 * here says Reported once sent, as the bar's would.
 */
@Composable
internal fun PlayerFailure(message: String, onRetry: () -> Unit, onReport: () -> Unit, report: ReportUi) {
    val sent = report.stage == ReportStage.SENT
    val enabled = !sent && report.stage != ReportStage.SENDING
    Box(modifier = Modifier.fillMaxSize().padding(Spacing.large), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = message, color = Color.White, style = MaterialTheme.typography.bodyLarge)
            CatalogueTextButton(onClick = onRetry) {
                Text(text = "Retry", color = Color.White)
            }
            CatalogueTextButton(onClick = onReport, enabled = enabled) {
                Text(text = if (sent) ReportWords.SENT else ReportWords.BUTTON, color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f))
            }
        }
    }
}

/**
 * What the stage says while the title opens (the film leader) or after it
 * failed (the message, Retry and Report), and the report sheet Report opens.
 * The player's own look and the remote look of a presenting player both draw
 * it, so a title opening or failing on a TV is said on the phone.
 */
@Composable
internal fun PlayerStageNote(state: PlayerUiState, report: ReportUi, viewModel: PlayerViewModel) {
    val upNext by viewModel.upNext.collectAsStateWithLifecycle()
    val warmUp = viewModel.bufferedAheadMs.takeIf { upNext.awaitingStart }
    when (state) {
        PlayerUiState.Preparing -> PreparingNote(warmUp)
        is PlayerUiState.Failed -> PlayerFailure(state.message, onRetry = viewModel::retry, onReport = { viewModel.openReport(fromFailure = true) }, report = report)
        PlayerUiState.Playing, PlayerUiState.Paused -> if (warmUp != null) PreparingNote(warmUp)
    }
    if (report.open) ReportDialog(report, onSend = viewModel::sendReport, onDismiss = viewModel::closeReport)
}

package ui.tv.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import designsystem.Accent
import designsystem.CountdownLeader
import designsystem.LocalCountdownStart
import designsystem.Overscan
import designsystem.Spacing
import model.ReportWords
import player.PlayerUiState
import ui.tv.TvLeaderSize

/** What the stage says while there is no film to show: the film leader while it prepares, the failure once it has failed. */
@Composable
internal fun TvPlayerStatus(
    state: PlayerUiState,
    retry: FocusRequester,
    onRetry: () -> Unit,
    onReport: () -> Unit,
) {
    when (state) {
        PlayerUiState.Preparing -> TvPreparingLeader()
        is PlayerUiState.Failed -> TvPlayerFailure(state.message, retry, onRetry, onReport)
        PlayerUiState.Playing, PlayerUiState.Paused -> Unit
    }
}

/**
 * The stage while the title opens: the film leader alone, centred, as the
 * web's stage shows it (`index.html:147`, `film-leader.css:56-58`). Its sweep
 * is the stage's fixed coral (`--stage-accent`, `theme.css:97`;
 * `film-leader.css:34`), whatever the viewer's accent, because the stage is
 * always black. The leader says nothing, so TalkBack hears "Loading" here.
 */
@Composable
private fun TvPreparingLeader() {
    Box(
        modifier = Modifier.fillMaxSize().semantics { contentDescription = "Loading" },
        contentAlignment = Alignment.Center,
    ) {
        // A wait of its own, so its count starts at 3, not where the cold start's had got to.
        CompositionLocalProvider(LocalCountdownStart provides null) { CountdownLeader(size = TvLeaderSize, accent = Accent.CORAL.dark) }
    }
}

/**
 * A failed title's message, Retry and Report — the phone's `PlayerFailure`.
 * Retry re-opens the same title at wherever it was last saved to; most
 * failures are the network rather than a broken file, so that is usually
 * enough. Report beside it opens the report on "Won't play" ([onReport]).
 *
 * The remote lands on Retry the moment the failure shows ([retry]): it is
 * the first thing to try, and a viewer whose film just stopped should not
 * have to find it first. Right reaches Report.
 */
@Composable
internal fun TvPlayerFailure(
    message: String,
    retry: FocusRequester,
    onRetry: () -> Unit,
    onReport: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
        Text(text = message, style = TvPlayerType.body, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            TvOverlayButton(
                text = "Retry",
                style = TvPlayerType.body,
                enabled = true,
                onClick = onRetry,
                modifier = Modifier.focusRequester(retry),
            )
            TvOverlayButton(text = ReportWords.BUTTON, style = TvPlayerType.body, enabled = true, onClick = onReport)
        }
    }
}

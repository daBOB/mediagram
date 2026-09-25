package ui.tv.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.Text
import designsystem.Overscan
import designsystem.Palette
import designsystem.Spacing
import designsystem.TvTypeScale
import player.PlayerUiState
import ui.tv.setup.TvLoadingIndicator

/** What the stage says while there is no film to show: the spinner while it prepares, the failure once it has failed. */
@Composable
internal fun TvPlayerStatus(
    state: PlayerUiState,
    retry: FocusRequester,
    onRetry: () -> Unit,
) {
    when (state) {
        PlayerUiState.Preparing -> TvLoadingIndicator()
        is PlayerUiState.Failed -> TvPlayerFailure(state.message, retry, onRetry)
        PlayerUiState.Playing, PlayerUiState.Paused -> Unit
    }
}

/**
 * A failed title's message, and Retry — the phone's `PlayerFailure`, which
 * re-opens the same title at wherever it was last saved to. Most failures
 * are the network rather than a broken file, so that is usually enough.
 *
 * The remote lands on Retry the moment the failure shows ([retry]): it is
 * the only thing on screen that can be pressed, and a viewer whose film
 * just stopped should not have to find it first.
 */
@Composable
internal fun TvPlayerFailure(
    message: String,
    retry: FocusRequester,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
        Text(text = message, style = TvTypeScale.body, color = Palette.Text, textAlign = TextAlign.Center)
        TvOverlayButton(
            text = "Retry",
            style = TvTypeScale.body,
            enabled = true,
            onClick = onRetry,
            modifier = Modifier.focusRequester(retry),
        )
    }
}

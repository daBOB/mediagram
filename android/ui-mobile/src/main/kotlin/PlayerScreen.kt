package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.compose.PlayerSurface
import designsystem.Spacing
import player.PlayerUiState
import player.PlayerViewModel

/**
 * Hosts the shared [PlayerViewModel] behind a `PlayerSurface`, keeping the
 * screen awake while a set is actually playing. Touch controls are
 * intentionally minimal for this round: back, and a loading/error overlay
 * — a scrubber is phase 6 work.
 */
@Composable
fun PlayerScreen(setId: String, onBack: () -> Unit) {
    val viewModel: PlayerViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(setId) { viewModel.open(setId) }
    KeepScreenOnWhile(isPlaying = state is PlayerUiState.Playing)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        PlayerSurface(player = viewModel.player, modifier = Modifier.fillMaxSize())

        when (state) {
            PlayerUiState.Preparing -> CenteredSpinner()
            is PlayerUiState.Failed -> CenteredError((state as PlayerUiState.Failed).message)
            is PlayerUiState.Playing, is PlayerUiState.Paused -> Unit
        }

        IconButton(onClick = onBack, modifier = Modifier.padding(Spacing.medium)) {
            Text(text = "←", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun KeepScreenOnWhile(isPlaying: Boolean) {
    val view = LocalView.current
    DisposableEffect(isPlaying) {
        view.keepScreenOn = isPlaying
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun CenteredSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.White)
    }
}

@Composable
private fun CenteredError(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(Spacing.large), contentAlignment = Alignment.Center) {
        Text(text = message, color = Color.White, style = MaterialTheme.typography.bodyLarge)
    }
}

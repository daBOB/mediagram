package ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Keeps the display awake while [isPlaying] is true, and releases that hold
 * as soon as it is not — a paused or stopped film is not worth burning the
 * screen for, and nothing else on the player screen has a reason to touch
 * this flag.
 */
@Composable
fun KeepScreenOnWhile(isPlaying: Boolean) {
    val view = LocalView.current
    DisposableEffect(isPlaying) {
        view.keepScreenOn = isPlaying
        onDispose { view.keepScreenOn = false }
    }
}

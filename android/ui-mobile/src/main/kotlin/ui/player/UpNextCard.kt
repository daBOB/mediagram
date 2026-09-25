package ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import designsystem.Spacing
import player.UpNextPhase
import player.UpNextUiState

/**
 * The panel over the picture in the last moments of a title — "Up next" and
 * its own title line, a way to jump to it now, and a way to say no. A port
 * of `#up-next` (`index.html`) + `refreshUpNext` (`player.js`);
 * [UpNextUiState.phase] decides whether it is on screen at all.
 *
 * [barTop]/[screenBottom]/[pictureBottom] are root-coordinate measurements
 * from `PlayerScreen` — `null` for [barTop] while the transport bar is
 * hidden, meaning nothing of *it* to clear. The card still has to clear
 * something else then: immersive mode (`ImmersiveEffect`) leaves the bar
 * hidden over a picture that a letterboxing framing may not reach all the
 * way to the screen's own bottom, and a card anchored past the picture's
 * own edge would hang its background and buttons in the black band below
 * it. So while the bar is hidden this clears [pictureBottom] instead — the
 * same measurement `SubtitleLayer` already clamps its own text to — and
 * while it is shown this clears the bar exactly as far as it actually
 * covers, same as before. Either way it sits inside the system's own
 * bottom inset too, so a three-button navigation bar never clips its own
 * buttons.
 */
@Composable
internal fun UpNextCard(
    state: UpNextUiState,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
    barTop: Float?,
    pictureBottom: Float?,
    screenBottom: Float?,
    modifier: Modifier = Modifier,
) {
    if (state.phase == UpNextPhase.HIDDEN) return
    // Nothing to clear once `screenBottom` itself is unknown (the very first
    // frame) — `0.dp` then, the same as before either of these existed.
    val clearAbove = barTop ?: pictureBottom ?: screenBottom
    val liftForBar = with(LocalDensity.current) {
        if (screenBottom != null && clearAbove != null) (screenBottom - clearAbove).coerceAtLeast(0f).toDp() else 0.dp
    }
    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(bottom = liftForBar)
            .widthIn(max = 360.dp)
            .background(Color.Black.copy(alpha = SCRIM_ALPHA), RoundedCornerShape(Spacing.small))
            .padding(Spacing.medium),
    ) {
        Text(text = "Up next", color = Color.White, style = MaterialTheme.typography.labelLarge)
        if (state.titleLine.isNotEmpty()) {
            Text(text = state.titleLine, color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = if (state.phase == UpNextPhase.COUNTING) {
                "Starting in ${state.countdownSecondsLeft ?: 0}…"
            } else {
                "When this ends"
            },
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            TextButton(onClick = onPlayNow) { Text("Play now", color = Color.White) }
            TextButton(onClick = onCancel) { Text("Cancel", color = Color.White) }
        }
    }
}

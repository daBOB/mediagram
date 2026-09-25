// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.media3.common.Player
import designsystem.Spacing
import model.MediaSet
import playback.PlaybackTotals
import player.titleLine

/**
 * The pieces `PlayerScreen` draws, apart from the screen that arranges
 * them. Each is handed what it needs and decides nothing about when it
 * appears; the decisions stay with the screen and with
 * [ControlsVisibility]'s own functions, which is where they can be read
 * and proved.
 */

@Composable
internal fun CenteredSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.White)
    }
}

/**
 * The back arrow, the picture-in-picture button beside it, the title and
 * the statistics — split out of `PlayerScreen` to keep that file under the
 * project's line guideline. Absent entirely in picture-in-picture: there
 * is no touch surface of this app's own inside that window, and no room
 * for the statistics either.
 *
 * Placed by its caller with `Modifier.align(Alignment.TopStart)` rather
 * than centred here: the picture is what a `Box` centres its children
 * against, and the top bar has to be pinned to its own corner instead.
 */
@Composable
internal fun PlayerTopChrome(
    openSet: MediaSet?,
    barShown: Boolean,
    statsShown: Boolean,
    isInPip: Boolean,
    player: Player?,
    totals: () -> PlaybackTotals,
    onBack: () -> Unit,
    onEnterPip: (() -> Unit)?,
    modifier: Modifier = Modifier,
    held: Boolean = false,
    onNotes: (() -> Unit)? = null,
) {
    if (isInPip) return
    Column(modifier = modifier) {
        PlayerTopBar(title = titleLine(openSet), showTitle = barShown, onBack = onBack, onEnterPip = onEnterPip, onNotes = onNotes)
        // Gated on the bar being shown as well as on the toggle, so the
        // statistics have no visibility rule of their own: a viewer who
        // leaves the numbers on gets the picture back when the bar takes
        // itself away, and keeps them while the film is paused.
        if (statsShown && barShown) {
            player?.let { current ->
                PlaybackStatsOverlay(player = current, totals = totals, held = held, modifier = Modifier.padding(start = Spacing.medium))
            }
        }
    }
}

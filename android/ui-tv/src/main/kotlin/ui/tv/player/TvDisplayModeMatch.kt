package ui.tv.player

import android.view.Display
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import playback.Mode
import playback.pickDisplayMode
import ui.player.findActivity

/**
 * Asks the television to switch to a refresh rate the film divides into
 * evenly, once the selected video track's frame rate is known, and puts the
 * preference back when the screen goes.
 *
 * TV-only by placement: a browser cannot switch display modes, and phones
 * and tablets keep media3's default (switch only when seamless), which
 * already does the switches their panels allow. For a heavy switch, which
 * blanks the screen while HDMI re-syncs, Android's frame-rate guide names
 * `preferredDisplayModeId`, and a television is where it belongs.
 */
@Composable
internal fun TvDisplayModeMatch(player: Player?) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(player, activity) {
        val window = activity?.window
        if (player == null || window == null) return@DisposableEffect onDispose {}
        val before = window.attributes.preferredDisplayModeId

        fun match(tracks: Tracks) {
            val fps =
                tracks.groups
                    .filter { it.type == C.TRACK_TYPE_VIDEO }
                    .firstNotNullOfOrNull { group ->
                        (0 until group.length)
                            .firstOrNull { group.isTrackSelected(it) }
                            ?.let { group.getTrackFormat(it).frameRate }
                    } ?: return
            val display = window.decorView.display ?: return
            val id = pickDisplayMode(display.mode.toMode(), display.supportedModes.map { it.toMode() }, fps) ?: return
            window.attributes = window.attributes.apply { preferredDisplayModeId = id }
        }
        val listener =
            object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) = match(tracks)
            }
        player.addListener(listener)
        match(player.currentTracks)
        onDispose {
            player.removeListener(listener)
            window.attributes = window.attributes.apply { preferredDisplayModeId = before }
        }
    }
}

private fun Display.Mode.toMode() = Mode(modeId, physicalWidth, physicalHeight, refreshRate)

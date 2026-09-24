// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.media3.common.Player
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.state.rememberPresentationState

/**
 * The pieces `PlayerScreen` draws, apart from the screen that arranges
 * them. Each is handed what it needs and decides nothing about when it
 * appears; the decisions stay with the screen and with
 * [ControlsVisibility]'s own functions, which is where they can be read
 * and proved.
 */

/**
 * The picture, shaped to itself rather than to the screen.
 *
 * `PlayerSurface` draws into whatever bounds it is given and applies no
 * ratio of its own — the old `PlayerView` had a frame layout that did — so
 * filling the window stretches a 2.4:1 film onto a 3:2 display and makes
 * everyone in it tall and thin. The black behind is the letterbox.
 *
 * The size comes from media3's own presentation state rather than from a
 * listener written here: it already folds in the pixel shape that makes
 * anamorphic video 2.4:1 rather than 1.78:1, and it already knows when the
 * surface is showing a frame that no longer belongs to what is playing.
 *
 * [overlay] draws inside this same box — the picture's own rectangle, not
 * the screen's — which is what lets `SubtitleLayer` sit bottom-centred
 * against the video itself rather than against whatever letterbox surrounds
 * it.
 */
@Composable
internal fun Video(player: Player, overlay: @Composable BoxScope.() -> Unit = {}) {
    val presentation = rememberPresentationState(player)
    val size = presentation.videoSizeDp
    val shaped = if (size != null && size.width > 0f && size.height > 0f) {
        Modifier.fillMaxSize().aspectRatio(size.width / size.height)
    } else {
        Modifier.fillMaxSize()
    }
    Box(modifier = shaped) {
        PlayerSurface(player = player, modifier = Modifier.fillMaxSize())
        // Between one set and the next the surface still holds the last
        // frame of the old one. Covering it is what media3 asks callers to
        // do, and the alternative is a still from the previous film over
        // the new one's audio.
        if (presentation.coverSurface) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black))
        }
        overlay()
    }
}

@Composable
internal fun KeepScreenOnWhile(isPlaying: Boolean) {
    val view = LocalView.current
    DisposableEffect(isPlaying) {
        view.keepScreenOn = isPlaying
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
internal fun CenteredSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.White)
    }
}

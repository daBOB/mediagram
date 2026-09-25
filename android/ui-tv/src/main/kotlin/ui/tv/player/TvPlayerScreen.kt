package ui.tv.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import model.MediaSet
import player.CONTROLS_LINGER_MS
import player.PlayerUiState
import player.PlayerViewModel
import player.controlsMayShow
import player.controlsShouldFade
import ui.player.KeepScreenOnWhile
import ui.player.PlayerLifecycle
import ui.player.Video
import ui.tv.catalog.TvCenteredMessage
import ui.tv.setup.TvLoadingIndicator

/** Finds the screen itself in a test: the node that holds the remote while the controls are away. */
internal const val TvPlayerScreenTag = "tv-player-screen"

/**
 * A title playing on a television, driven by the remote — the phone's
 * `PlayerScreen` with keys where the phone has taps. The same lifecycle
 * ([PlayerLifecycle]: Home saves, leaving stops, a configuration change
 * does neither), the same screen-on rule, the same picture, and controls
 * that fade by the same shared rule on the same clock: shown when the
 * screen opens, gone after a while of playing, never while paused.
 *
 * Every key is read once, here, before anything focused sees it, and
 * answered by [tvKeyAction]'s table through [TvPlayerRemote]. While the
 * controls are away the screen itself holds the remote, so no key is ever
 * lost to a focus that went with them; while they are up it cannot be
 * focused at all, so moving around them never lands on the picture.
 *
 * [set] is the catalogue's entry for [setId], for what the top bar says
 * and the runtime the end time is read from; its age rating is what the
 * player is opened with, as the phone opens it. Null while the catalogue
 * has no entry for it, which leaves only the top bar out.
 */
@Composable
fun TvPlayerScreen(
    setId: String,
    set: MediaSet?,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()

    PlayerLifecycle(viewModel = viewModel, setId = setId, fsk = set?.fsk)
    KeepScreenOnWhile(isPlaying = state is PlayerUiState.Playing)

    var controlsShown by remember { mutableStateOf(true) }
    var landing by remember { mutableStateOf(TvControlsLanding.PlayPause) }
    var onSeekBar by remember { mutableStateOf(false) }
    // Every press restarts the fade: a viewer working through the
    // controls with the remote is using them, the way a pointer moving
    // over the web's is.
    var presses by remember { mutableIntStateOf(0) }
    LaunchedEffect(controlsShown, state, presses) {
        if (!controlsShown) return@LaunchedEffect
        if (!controlsShouldFade(isPlaying = state is PlayerUiState.Playing, isScrubbing = false)) return@LaunchedEffect
        delay(CONTROLS_LINGER_MS)
        controlsShown = false
    }

    val barShown = controlsShown && controlsMayShow(state) && player != null
    val root = remember { FocusRequester() }
    val focus = remember { TvPlayerFocus() }
    val remote =
        remember {
            TvPlayerRemote { to ->
                landing = to
                controlsShown = true
            }
        }
    // Wherever the controls go, the remote goes with them: onto the
    // control the key that raised them asked for, or back to the screen
    // itself when they leave.
    LaunchedEffect(barShown) {
        when {
            !barShown -> root.requestFocus()
            landing == TvControlsLanding.SeekBar -> focus.seekBar.requestFocus()
            else -> focus.playPause.requestFocus()
        }
    }

    // The table's Back row, answered here rather than as a key so a Back
    // that is not one — a gesture, the dispatcher itself — does the same.
    BackHandler {
        when (tvKeyAction(Key.Back, controlsShowing = barShown, focusInControls = onSeekBar)) {
            TvKeyAction.HideControls -> controlsShown = false
            else -> onBack()
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) presses++
                    remote.onKey(event, player, controlsShowing = barShown, onSeekBar = onSeekBar)
                }.focusRequester(root)
                .focusProperties { canFocus = !barShown }
                .focusable()
                .testTag(TvPlayerScreenTag),
        contentAlignment = Alignment.Center,
    ) {
        player?.let { current ->
            Video(current)
            if (barShown) {
                TvPlayerControls(player = current, set = set, focus = focus, onSeekBarFocused = { onSeekBar = it })
            }
        }
        when (val now = state) {
            PlayerUiState.Preparing -> TvLoadingIndicator()
            is PlayerUiState.Failed -> TvCenteredMessage(now.message)
            PlayerUiState.Playing, PlayerUiState.Paused -> Unit
        }
    }
}

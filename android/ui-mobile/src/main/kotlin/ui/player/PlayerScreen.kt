// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import designsystem.Spacing
import kotlinx.coroutines.delay
import player.CONTROLS_LINGER_MS
import player.PlayerUiState
import player.PlayerViewModel
import player.controlsMayShow
import player.controlsShouldFade

/**
 * Keeps playback through Activity recreation and stops when navigation removes
 * the screen. A tap shows the controls; they fade while playing and stay while
 * paused or being scrubbed. The Activity-scoped ViewModel survives rotation.
 *
 * The toggles are inset by the system bars even while the player hides them,
 * which Compose still marks experimental.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(
    setId: String,
    fsk: String?,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val marks by viewModel.marks.collectAsStateWithLifecycle()
    val actionNotice by viewModel.actionNotice.collectAsStateWithLifecycle()

    PlayerLifecycle(viewModel = viewModel, setId = setId, fsk = fsk)
    KeepScreenOnWhile(isPlaying = state is PlayerUiState.Playing)

    // Shown when the screen opens, so a viewer finds out the bar is there at
    // all, then left to take itself away.
    var controlsShown by remember { mutableStateOf(true) }
    var scrubbing by remember { mutableStateOf(false) }
    // Saved, because a rotation destroys this composition and a viewer who
    // turned the phone to read a wider row did not ask for the numbers back.
    var statsShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(controlsShown, state, scrubbing) {
        if (!controlsShown) return@LaunchedEffect
        val fades =
            controlsShouldFade(
                isPlaying = state is PlayerUiState.Playing,
                isScrubbing = scrubbing,
            )
        if (!fades) return@LaunchedEffect
        delay(CONTROLS_LINGER_MS)
        controlsShown = false
    }

    // One predicate, read twice, because the bar and the statistics sit in
    // different corners and cannot be nested under a single `if`. Both are
    // the bar being on screen, so both ask the same question rather than two
    // that could drift apart.
    val barShown = controlsShown && controlsMayShow(state)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) { detectTapGestures { controlsShown = !controlsShown } },
        contentAlignment = Alignment.Center,
    ) {
        player?.let { current ->
            Video(current)
            if (barShown) {
                PlayerControls(
                    player = current,
                    onScrubbingChanged = { scrubbing = it },
                    statsShown = statsShown,
                    onToggleStats = { statsShown = !statsShown },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                // Top-right, opposite back: the web keeps these in the
                // player because "this is where a viewer is when they find
                // out what a film actually is", not because of where on the
                // page they sit — this platform's own transport bar already
                // owns the bottom edge.
                //
                // Below the status bar's band, not in it: flush to the top
                // they shared the strip the system reserves for its own
                // gestures, and taps there went to the system. Measured
                // against the bars even while they are hidden, so the row
                // does not jump up when the picture goes full screen.
                PlayerMarks(
                    marks = marks,
                    notice = actionNotice,
                    actions =
                        PlayerMarksActions(
                            onToggleWatchlist = viewModel::toggleWatchlist,
                            onToggleKids = viewModel::toggleKids,
                            onSetInList = viewModel::setInList,
                            onCreateList = viewModel::createListAndAdd,
                        ),
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility)
                            .padding(Spacing.medium),
                )
            }
        }

        when (state) {
            PlayerUiState.Preparing -> CenteredSpinner()
            is PlayerUiState.Failed -> CenteredError((state as PlayerUiState.Failed).message)
            PlayerUiState.Playing, PlayerUiState.Paused -> Unit
        }

        actionNotice?.let { notice ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(Spacing.medium),
                dismissAction = {
                    TextButton(onClick = viewModel::dismissActionNotice) { Text("Dismiss") }
                },
            ) { Text(notice) }
        }

        // Placed explicitly: the box centres its children so the picture
        // sits in the middle of its letterbox, and back would otherwise be
        // centred with it, in the middle of the film.
        //
        // The statistics sit under back in a column rather than at their own
        // corner, so there is no arithmetic anywhere that has to know how
        // tall the arrow is in order to clear it.
        Column(modifier = Modifier.align(Alignment.TopStart)) {
            IconButton(onClick = onBack, modifier = Modifier.padding(Spacing.medium)) {
                Text(text = "←", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            }
            // Gated on the bar being shown as well as on the toggle, so the
            // statistics have no visibility rule of their own: a viewer who
            // leaves the numbers on gets the picture back when the bar takes
            // itself away, and keeps them while the film is paused.
            if (statsShown && barShown) {
                player?.let { current ->
                    PlaybackStatsOverlay(
                        player = current,
                        totals = viewModel.totals,
                        modifier = Modifier.padding(start = Spacing.medium),
                    )
                }
            }
        }
    }
}

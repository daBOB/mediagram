// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import designsystem.Spacing
import kotlinx.coroutines.delay
import player.titleLine
import player.PlayerUiState
import player.PlayerViewModel

/**
 * Hosts the shared [PlayerViewModel] behind a `PlayerSurface`, keeping the
 * screen awake while a set is actually playing and stopping playback when
 * this leaves composition for real — not on a rotation, which destroys
 * and recreates this same composition too (there is no
 * `android:configChanges`) while the singleton player/ViewModel underneath
 * survive regardless; see [shouldStopOnDispose].
 *
 * A tap toggles the transport bar, which takes itself away while a film runs
 * and stays while it is paused, being scrubbed, or the settings sheet is
 * open; see [controlsShouldFade].
 */
// The toggles are inset by the system bars even while the player hides them,
// which Compose still marks experimental.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(setId: String, fsk: String?, onBack: () -> Unit) {
    val viewModel: PlayerViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val marks by viewModel.marks.collectAsStateWithLifecycle()
    val openSet by viewModel.openSet.collectAsStateWithLifecycle()
    val choices by viewModel.choices.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()

    LaunchedEffect(setId) { viewModel.open(setId, fsk) }
    DisposableEffect(Unit) {
        onDispose {
            if (shouldStopOnDispose(activity?.isChangingConfigurations == true)) {
                viewModel.stop()
            }
        }
    }

    // Backstop for a kill that skips onDispose entirely — recents swiped,
    // the process trimmed. Not a duplicate of the DisposableEffect above:
    // that one only runs when this Composition is actually torn down, and
    // an Activity can reach ON_STOP (screen off, task-switched away) while
    // the Composition it hosts is still there, primed to resume.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.save()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    KeepScreenOnWhile(isPlaying = state is PlayerUiState.Playing)

    // Shown when the screen opens, so a viewer finds out the bar is there at
    // all, then left to take itself away.
    var controlsShown by remember { mutableStateOf(true) }
    var scrubbing by remember { mutableStateOf(false) }
    var settingsShown by remember { mutableStateOf(false) }
    // Saved, because a rotation destroys this composition and a viewer who
    // turned the phone to read a wider row did not ask for the numbers back.
    var statsShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(controlsShown, state, scrubbing, settingsShown) {
        if (!controlsShown || settingsShown) return@LaunchedEffect
        val fades = controlsShouldFade(isPlaying = state is PlayerUiState.Playing, isScrubbing = scrubbing)
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
        modifier = Modifier
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
                    speed = choices.speed,
                    onOpenSettings = { settingsShown = true },
                    catalogedDurationSecs = openSet?.durationSecs,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                // Top-right, opposite back: the web keeps these in the player
                // because "this is where a viewer finds out what a film
                // actually is", not because of where on the page they sit —
                // this platform's transport bar already owns the bottom edge.
                // Below the status bar's band, measured against the system
                // bars even while hidden, so the row does not jump on fullscreen.
                PlayerMarks(
                    marks = marks,
                    actions = PlayerMarksActions(
                        onToggleWatchlist = viewModel::toggleWatchlist,
                        onToggleKids = viewModel::toggleKids,
                        onSetInList = viewModel::setInList,
                        onCreateList = viewModel::createListAndAdd,
                    ),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility)
                        .padding(Spacing.medium),
                )
            }
            if (settingsShown) {
                PlayerSettingsSheet(
                    currentSpeed = choices.speed,
                    onSpeedChosen = viewModel::setSpeed,
                    onDismiss = { settingsShown = false },
                )
            }
        }

        when (state) {
            PlayerUiState.Preparing -> CenteredSpinner()
            is PlayerUiState.Failed -> PlayerFailure((state as PlayerUiState.Failed).message, onRetry = viewModel::retry)
            PlayerUiState.Playing, PlayerUiState.Paused -> Unit
        }

        // Placed explicitly: the box centres its children, and the top bar
        // would otherwise be centred with the picture rather than pinned to
        // its corner. The statistics sit under it in the same column, so
        // nothing here has to know how tall the bar is to clear it.
        Column(modifier = Modifier.align(Alignment.TopStart)) {
            PlayerTopBar(title = titleLine(openSet), showTitle = barShown, onBack = onBack)
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

/**
 * A rotation disposes and recreates this screen's whole composition
 * exactly the way leaving it for the catalog does; the two are told apart
 * by whether the Activity itself is mid configuration change. Stopping on
 * a rotation would restart the same set from zero every time the device
 * turns, which is worse than the drop-to-catalog bug this replaced.
 */
internal fun shouldStopOnDispose(isChangingConfigurations: Boolean): Boolean = !isChangingConfigurations

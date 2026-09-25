// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import designsystem.Spacing
import player.UpNextPhase
import player.PlayerUiState
import player.PlayerViewModel
import player.controlsMayShow
import player.chooseFraming
import player.createListAndAdd
import player.retry
import player.setInList
import player.toggleKids
import player.toggleWatchlist

/**
 * Hosts the shared [PlayerViewModel] behind a `PlayerSurface`, keeping the
 * screen awake while a set is actually playing and stopping playback when
 * this leaves composition for real — never for a rotation or a
 * picture-in-picture resize, both declared in the manifest's own
 * `android:configChanges` so the activity is never recreated for either;
 * this composition simply reflows at the new size instead, and the
 * singleton player/ViewModel underneath were never touched either way.
 * See [shouldStopOnDispose].
 *
 * A tap toggles the transport bar, which takes itself away while a film runs
 * and stays while it is paused, being scrubbed, or the settings sheet is
 * open; see [controlsShouldFade]. Double-tap seeking, play/pause and pinch
 * framing live in [PlayerGestureLayer], which wraps everything below. All of
 * it — transport bar, marks, sheet, up-next card, top bar — is hidden while
 * [LocalIsInPictureInPicture] is true; see [PipController].
 */
// The toggles are inset by the system bars even while the player hides them,
// which Compose still marks experimental.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(
    setId: String,
    run: List<String>,
    fsk: String?,
    onBack: () -> Unit,
    onSwitch: (setId: String, run: List<String>) -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val marks by viewModel.marks.collectAsStateWithLifecycle()
    val openSet by viewModel.openSet.collectAsStateWithLifecycle()
    val choices by viewModel.choices.collectAsStateWithLifecycle()
    val subtitleCues by viewModel.subtitleCues.collectAsStateWithLifecycle()
    val upNext by viewModel.upNext.collectAsStateWithLifecycle()
    val held by viewModel.held.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val actionNotice by viewModel.actionNotice.collectAsStateWithLifecycle()
    val isInPip = LocalIsInPictureInPicture.current
    val pip = PipController(player = player, isPlaying = state is PlayerUiState.Playing, onDismissed = viewModel::pauseForPipDismissal)

    PlayerNavigationEffects(viewModel, setId, run, fsk, onSwitch)
    PlayerLifecycleEffects(
        viewModel,
        // The countdown drops `isPlaying` (the title has ended) and the gate
        // wait pauses on purpose; neither is a viewer looking away.
        isPlaying = state is PlayerUiState.Playing || upNext.phase != UpNextPhase.HIDDEN || upNext.awaitingStart,
    )

    // Shown when the screen opens, so a viewer finds out the bar is there at
    // all, then left to take itself away.
    var controlsShown by remember { mutableStateOf(true) }
    var scrubbing by remember { mutableStateOf(false) }
    var settingsShown by remember { mutableStateOf(false) }
    // Saved, because a rotation destroys this composition and a viewer who
    // turned the phone to read a wider row did not ask for the numbers back.
    var statsShown by rememberSaveable { mutableStateOf(false) }
    var barTop by remember { mutableStateOf<Float?>(null) }
    ControlsAutoHide(controlsShown, isPlaying = state is PlayerUiState.Playing, scrubbing, settingsShown, onHide = { controlsShown = false })

    // One predicate, read twice, because the bar and the statistics sit in
    // different corners and cannot be nested under a single `if`. Both are
    // the bar being on screen, so both ask the same question rather than two
    // that could drift apart. `!isInPip` folds in here too: there is no
    // touch surface of this app's own inside that window to show a bar on.
    val barShown = controlsShown && controlsMayShow(state) && !isInPip

    // The up-next card appearing is itself a reason to bring the bar back —
    // a viewer who let it fade is exactly who most wants to see the panel.
    LaunchedEffect(upNext.phase) { if (upNext.phase != UpNextPhase.HIDDEN) controlsShown = true }

    // Root-coordinate measurements the up-next card clamps to; see UpNextCard.
    var screenBottom by remember { mutableStateOf<Float?>(null) }
    var pictureBottom by remember { mutableStateOf<Float?>(null) }

    NotesLayout(notes, isInPip, onClose = viewModel::toggleNotes) {
        PlayerGestureLayer(
            player = player,
            onToggleControls = { controlsShown = !controlsShown },
            onPinchFraming = viewModel::chooseFraming,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onGloballyPositioned { screenBottom = it.boundsInRoot().bottom },
        ) {
            player?.let { current ->
                VideoWithSubtitles(current, subtitleCues, choices, barTop = barTop.takeIf { barShown }, isInPip = isInPip, onPictureBottomChanged = { pictureBottom = it })
                if (barShown) {
                    PlayerControls(
                        player = current,
                        onScrubbingChanged = { scrubbing = it },
                        statsShown = statsShown,
                        onToggleStats = { statsShown = !statsShown },
                        speed = choices.speed,
                        onOpenSettings = { settingsShown = true },
                        catalogedDurationSecs = openSet?.durationSecs,
                        hasNext = upNext.hasNext,
                        nextTitleLine = upNext.titleLine,
                        onPlayNext = viewModel::playNext,
                        modifier = Modifier.align(Alignment.BottomCenter).onGloballyPositioned { barTop = it.boundsInRoot().top },
                    )
                    // Top-right, opposite back: the web keeps these in the player
                    // because "this is where a viewer finds out what a film
                    // actually is", not because of where on the page they sit —
                    // this platform's transport bar already owns the bottom edge.
                    // Below the status bar's band, measured against the system
                    // bars even while hidden, so the row does not jump on fullscreen.
                    PlayerMarks(
                        marks = marks,
                        notice = actionNotice,
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
                if (settingsShown && !isInPip) {
                    PlayerSettingsSheetForViewModel(
                        choices = choices,
                        viewModel = viewModel,
                        onDismiss = { settingsShown = false },
                    )
                }
                // The countdown that may run it keeps ticking either way — it
                // lives in the up-next controller, not in this composable.
                if (!isInPip) UpNextCard(
                    state = upNext,
                    onPlayNow = viewModel::playNext,
                    onCancel = viewModel::cancelUpNext,
                    barTop = barTop.takeIf { barShown },
                    pictureBottom = pictureBottom,
                    screenBottom = screenBottom,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            when (state) {
                PlayerUiState.Preparing -> CenteredSpinner()
                is PlayerUiState.Failed -> PlayerFailure((state as PlayerUiState.Failed).message, onRetry = viewModel::retry)
                PlayerUiState.Playing, PlayerUiState.Paused -> Unit
            }

            PlayerTopChrome(
                openSet = openSet,
                barShown = barShown,
                statsShown = statsShown,
                isInPip = isInPip,
                player = player,
                totals = viewModel.totals,
                onBack = onBack,
                onEnterPip = pip.enterPip.takeIf { pip.supported && player != null },
                modifier = Modifier.align(Alignment.TopStart),
                held = held,
                onNotes = viewModel::toggleNotes.takeIf { notes != null },
            )
            ActionNoticeBar(actionNotice, viewModel::dismissActionNotice, Modifier.align(Alignment.BottomCenter))
        }
    }
}

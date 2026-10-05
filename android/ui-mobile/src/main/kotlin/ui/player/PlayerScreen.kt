// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.round
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import player.PlayerUiState
import player.PlayerViewModel
import player.UpNextPhase
import player.chooseFraming
import player.controlsMayShow
import player.createListAndAdd
import player.playFromRun
import player.retry
import player.setInList
import player.setKidsMark
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
 * The controls are one card at the bottom of the picture
 * ([PlayerControlCard]) under a slim top bar ([PlayerTopChrome]). A tap
 * toggles both; they take themselves away while a film runs and stay while
 * it is paused, being scrubbed, or while a menu or the episode sidebar is
 * open — see [controlsShouldFade]. A tap on the picture with a menu open
 * closes the menu instead. Back closes the open menu, then the sidebar,
 * before the library's own Back is reached. Double-tap seeking, play/pause
 * and pinch framing live in [PlayerGestureLayer], which wraps everything
 * below. All of it is hidden while [LocalIsInPictureInPicture] is true; see
 * [PipController].
 */
@Composable
fun PlayerScreen(
    setId: String,
    run: List<String>,
    fsk: String?,
    handPicked: Boolean,
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
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val actionNotice by viewModel.actionNotice.collectAsStateWithLifecycle()
    val isInPip = LocalIsInPictureInPicture.current
    val pip = PipController(player = player, isPlaying = state is PlayerUiState.Playing, onDismissed = viewModel::pauseForPipDismissal)

    PlayerNavigationEffects(viewModel, setId, run, fsk, handPicked, onSwitch)
    PlayerLifecycleEffects(
        viewModel,
        // The countdown drops `isPlaying` (the title has ended) and the gate
        // wait pauses on purpose; neither is a viewer looking away.
        isPlaying = state is PlayerUiState.Playing || upNext.phase != UpNextPhase.HIDDEN || upNext.awaitingStart,
    )

    // Shown when the screen opens, so a viewer finds out the card is there
    // at all, then left to take itself away.
    var controlsShown by remember { mutableStateOf(true) }
    var scrubbing by remember { mutableStateOf(false) }
    // Saved, because a rotation destroys this composition and a viewer who
    // turned the phone to read a wider row did not ask for the numbers back.
    var statsShown by rememberSaveable { mutableStateOf(false) }
    val card = remember { PlayerCardState() }
    ControlsAutoHide(controlsShown, isPlaying = state is PlayerUiState.Playing, scrubbing, menuOrSidebarOpen = card.somethingOpen, onHide = { controlsShown = false })
    // Composed after the library's own Back, so it answers first — and only
    // while the card has something open to close.
    BackHandler(enabled = card.somethingOpen, onBack = card::closeTopmost)
    // `!isInPip` folds in here: there is no touch surface of this app's own
    // inside that window to show a card on.
    val barShown = controlsShown && controlsMayShow(state) && !isInPip
    // The up-next card appearing is itself a reason to bring the card back —
    // a viewer who let it fade is exactly who most wants to see the panel.
    LaunchedEffect(upNext.phase) { if (upNext.phase != UpNextPhase.HIDDEN) controlsShown = true }
    // A title with no run left has nothing to list.
    LaunchedEffect(episodes == null) { if (episodes == null) card.sidebarOpen = false }
    val barTop = card.bounds?.top?.toFloat()?.takeIf { barShown }

    // Root-coordinate measurements the up-next card clamps to; see UpNextCard.
    var screenBottom by remember { mutableStateOf<Float?>(null) }
    var pictureBottom by remember { mutableStateOf<Float?>(null) }

    NotesLayout(notes, isInPip, onClose = viewModel::toggleNotes) {
        PlayerGestureLayer(
            player = player,
            onToggleControls = { if (!card.dismissMenu()) controlsShown = !controlsShown },
            onPinchFraming = viewModel::chooseFraming,
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .onGloballyPositioned {
                        screenBottom = it.boundsInRoot().bottom
                        card.origin = it.positionInRoot().round()
                    },
        ) {
            player?.let { current ->
                VideoWithSubtitles(current, subtitleCues, choices, barTop = barTop, isInPip = isInPip, onPictureBottomChanged = { pictureBottom = it })
                if (barShown) {
                    PlayerControlCard(
                        player = current,
                        view = PlayerCardView(choices, upNext, statsShown, hasEpisodes = episodes != null, catalogedDurationSecs = openSet?.durationSecs),
                        actions = playerCardActions(viewModel, card, onToggleStats = { statsShown = !statsShown }, onEnterPip = pip.enterPip.takeIf { pip.supported }),
                        onScrubbingChanged = { scrubbing = it },
                        onBounds = { card.bounds = it },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                    CardMenuOverStage(card, choices, viewModel.cardMenuActions())
                }
                // The countdown that may run it keeps ticking either way — it
                // lives in the up-next controller, not in this composable.
                if (!isInPip) {
                    UpNextCard(
                        state = upNext,
                        onPlayNow = viewModel::playNext,
                        onCancel = viewModel::cancelUpNext,
                        barTop = barTop,
                        pictureBottom = pictureBottom,
                        screenBottom = screenBottom,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
            // Over the picture, which keeps playing beside it.
            episodes?.takeIf { card.sidebarOpen && !isInPip }?.let { list ->
                EpisodeSidebar(
                    list = list,
                    onPick = { id ->
                        card.sidebarOpen = false
                        viewModel.playFromRun(id)
                    },
                    onClose = { card.sidebarOpen = false },
                    modifier = Modifier.align(Alignment.CenterEnd),
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
                modifier = Modifier.align(Alignment.TopStart),
                held = held,
                onNotes = viewModel::toggleNotes.takeIf { notes != null },
                marks = {
                    PlayerMarks(
                        marks = marks,
                        notice = actionNotice,
                        actions =
                            PlayerMarksActions(
                                onToggleWatchlist = viewModel::toggleWatchlist,
                                onKidsMark = viewModel::setKidsMark,
                                onSetInList = viewModel::setInList,
                                onCreateList = viewModel::createListAndAdd,
                            ),
                    )
                },
            )
            ActionNoticeBar(actionNotice, viewModel::dismissActionNotice, Modifier.align(Alignment.BottomCenter))
        }
    }
}

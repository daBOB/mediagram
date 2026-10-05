package ui.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import designsystem.Overscan
import model.MediaSet
import player.PlayerUiState
import player.toggleSubtitles
import player.PlayerViewModel
import player.UpNextPhase
import player.controlsMayShow
import player.previous
import player.retry
import ui.player.KeepScreenOnWhile
import ui.player.PlayerLifecycle
import ui.player.PlayerNavigationEffects

/**
 * A title playing on a television, driven by the remote — the phone's
 * `PlayerScreen` with keys where the phone has taps: the same lifecycle
 * ([PlayerLifecycle]), screen-on rule, picture and subtitles, and controls
 * that fade by the same shared rule on the same clock, never while paused.
 *
 * Every key is read once, here, before anything focused sees it, and
 * answered by [tvKeyAction]'s table through [TvPlayerRemote]. While the
 * controls are away [TvPlayerKeyHolder] holds the remote.
 *
 * The tools open small menus above themselves ([TvCardMenuOverlay]); ☰
 * opens the episodes down the right ([TvEpisodeSidebar]); notes open in a
 * column beside the picture ([TvNotesBeside]). Back closes a menu or the
 * list, the up-next card, the notes, the statistics, then the controls
 * ([TvPlayerBack]). A failed title offers Retry ([TvPlayerStatus]).
 *
 * [set] is the catalogue's entry for [setId], for the top bar and the end
 * time; null while the catalogue has none. [run] is what the title plays
 * into — up next, ⏮ and ⏭ and the remote's Next and Previous walk it, and
 * [onSwitch] moves the library to another title of it. [handPicked] says
 * [run] is a list or the Kids wall, which takes nothing ahead.
 */
@Composable
fun TvPlayerScreen(
    setId: String,
    set: MediaSet?,
    run: List<String>,
    handPicked: Boolean,
    onBack: () -> Unit,
    onSwitch: (setId: String, run: List<String>) -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val marks by viewModel.marks.collectAsStateWithLifecycle()
    val actionNotice by viewModel.actionNotice.collectAsStateWithLifecycle()
    val held by viewModel.held.collectAsStateWithLifecycle()
    val choices by viewModel.choices.collectAsStateWithLifecycle()
    val subtitleCues by viewModel.subtitleCues.collectAsStateWithLifecycle()
    val upNext by viewModel.upNext.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val upNextShown = upNext.phase != UpNextPhase.HIDDEN
    val notesOpen = notes?.open == true
    val failed = state is PlayerUiState.Failed
    PlayerLifecycle(viewModel)
    PlayerNavigationEffects(viewModel, setId, run, set?.fsk, handPicked, onSwitch)
    // The phone's rule: the countdown drops playing (the title has ended)
    // and the wait for the next title's buffer pauses on purpose; neither
    // is a viewer looking away.
    KeepScreenOnWhile(isPlaying = state is PlayerUiState.Playing || upNextShown || upNext.awaitingStart)

    var controlsShown by remember { mutableStateOf(true) }
    var landing by remember { mutableStateOf(TvControlsLanding.PlayPause) }
    var onSeekBar by remember { mutableStateOf(false) }
    // Every press restarts the fade: a viewer working the remote is using the controls.
    var presses by remember { mutableIntStateOf(0) }
    val overlays = rememberTvPlayerOverlayState()
    // A switch to a title with no run takes its list away, and the sidebar with it.
    val sidebarShown = overlays.sidebarOpen && episodes != null
    val panelOpen = overlays.menu != null || sidebarShown
    val closePanel = {
        overlays.closePanel()
        landing = TvControlsLanding.Opener
    }
    val focus = remember { TvPlayerFocus() }
    TvPlayerOverlaysReset(setId, marks == null, player == null, closeList = overlays::closeMarkDialogs, closePanel = overlays::closeAllPanels, onTitleChanged = {
        overlays.menu = null
        landing = TvControlsLanding.PlayPause
        focus.opener = focus.playPause
    })
    TvControlsAutoHide(controlsShown, state, presses, held = overlays.choosingList || overlays.choosingKids || upNextShown, menuOrSidebarOpen = panelOpen, onHide = { controlsShown = false })
    // Up with the card and left up after it, as the phone brings its bar
    // back for it; the card counts as shown within the same frame, so the
    // remote lands on it rather than on a picture it is being taken from.
    LaunchedEffect(upNextShown) { if (upNextShown) controlsShown = true }

    val barShown = (controlsShown || upNextShown) && controlsMayShow(state) && player != null
    // Where the stage's bands are, for what floats between them to keep clear.
    val bands = remember { TvStageBands() }
    val root = remember { FocusRequester() }
    val notesFocus = remember { TvNotesFocus() }
    val remote =
        remember {
            TvPlayerRemote(
                show = { to ->
                    landing = to
                    controlsShown = true
                },
                onNext = viewModel::playNext,
                onPrevious = viewModel::previous,
                onToggleSubtitles = viewModel::toggleSubtitles,
            )
        }
    TvRemoteFollowsControls(barShown, panelOpen, upNextShown, landing, root, focus, failed, notesOpen = { notesOpen }, busy = { overlays.choosingList || overlays.choosingKids || onSeekBar }, onLanded = {
        landing = TvControlsLanding.PlayPause
        focus.opener = focus.playPause
    })
    TvNotesFollow(notesOpen, barShown, notesFocus, root, focus, busy = { panelOpen }, failed = { failed })
    TvPlayerBack(
        barShown = barShown,
        onSeekBar = onSeekBar,
        panelOpen = panelOpen,
        upNextShown = upNextShown,
        notesOpen = notesOpen,
        statsShown = overlays.statsShown,
        onClosePanel = closePanel,
        onCancelUpNext = viewModel::cancelUpNext,
        onCloseNotes = { notesFocus.closeFromBack(viewModel::toggleNotes) },
        onHideStats = { overlays.statsShown = false },
        onHideControls = { controlsShown = false },
        onLeave = onBack,
    )

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                // Not focusable itself: see TvPlayerKeyHolder for why the
                // one that holds the remote must never be an ancestor.
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) presses++
                    remote.onKey(event, player, barShown, onSeekBar, controlsMayShow(state), panelOpen, upNextShown, notesOpen)
                },
    ) {
        TvPlayerKeyHolder(root, canHold = !barShown)
        TvNotesBeside(notes, focus.notesRegion, notesFocus, button = focus.notes.takeIf { barShown }) {
            player?.let { current ->
                TvPlayerStage(
                    player = current,
                    set = set,
                    focus = focus,
                    viewModel = viewModel,
                    view = TvControlsView(marks, held, upNext, overlays.statsShown, choices, hasEpisodes = episodes != null, sidebarOpen = sidebarShown),
                    actions =
                        tvControlsActions(
                            overlays = overlays,
                            focus = focus,
                            viewModel = viewModel,
                            closePanel = closePanel,
                            onToggleNotes = notes?.let { { notesFocus.toggleFromButton(notesOpen, viewModel::toggleNotes) } },
                            onSeekBarFocused = { onSeekBar = it },
                            onPicked = { landing = TvControlsLanding.PlayPause },
                        ),
                    picture = TvStagePicture(subtitleCues, choices, barShown, overlays.menu, episodes.takeIf { sidebarShown }),
                    bands = bands,
                )
            }
            // A dialog window's own keys: the remote's media keys still reach
            // the film through it, as through a menu.
            val dialogKeys = { event: KeyEvent -> remote.onKey(event, player, controlsShowing = true, onSeekBar = false, canControl = controlsMayShow(state), panelOpen = true) }
            TvMarkDialogs(overlays, marks, actionNotice, viewModel, dialogKeys)
            TvActionNotice(
                notice = actionNotice,
                onGone = viewModel::dismissActionNotice,
                modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
            )
            TvPlayerStatus(state, focus.retry, onRetry = viewModel::retry)
        }
    }
}

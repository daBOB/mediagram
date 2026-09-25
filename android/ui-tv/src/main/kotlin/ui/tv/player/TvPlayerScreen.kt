package ui.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import designsystem.Overscan
import model.MediaSet
import player.PlayerUiState
import player.PlayerViewModel
import player.UpNextPhase
import player.controlsMayShow
import ui.player.KeepScreenOnWhile
import ui.player.PlayerLifecycle
import ui.player.PlayerNavigationEffects
import ui.tv.catalog.TvCenteredMessage
import ui.tv.setup.TvLoadingIndicator

/** Finds the screen itself in a test: the node that holds the remote while the controls are away. */
internal const val TvPlayerScreenTag = "tv-player-screen"

/**
 * A title playing on a television, driven by the remote — the phone's
 * `PlayerScreen` with keys where the phone has taps. The same lifecycle
 * ([PlayerLifecycle]: Home saves, leaving stops, a configuration change
 * does neither), the same screen-on rule, the same picture and subtitles
 * (drawn from the same shared settings, lifted clear of the controls while
 * they are up), and controls that fade by the same shared rule on the
 * same clock: shown when the screen opens, gone after a while of playing,
 * never while paused.
 *
 * Every key is read once, here, before anything focused sees it, and
 * answered by [tvKeyAction]'s table through [TvPlayerRemote]. While the
 * controls are away the screen itself holds the remote, so no key is ever
 * lost to a focus that went with them; while they are up it cannot be
 * focused at all, so moving around them never lands on the picture.
 *
 * The transport's gear opens the phone's playback settings as a panel to
 * one side ([TvPlayerSettingsPanel]); Back closes that before it does
 * anything else — before the controls go, before the player is left.
 *
 * [set] is the catalogue's entry for [setId], for what the top bar says
 * and the runtime the end time is read from; its age rating is what the
 * player is opened with, as the phone opens it. Null while the catalogue
 * has no entry for it, which leaves only the top bar out.
 *
 * [run] is what the title plays into, as on the phone: up next, the next
 * episodes taken ahead, and the remote's Next and Previous ([TvRunSteps])
 * all walk it; [onSwitch] moves the library to another title of it.
 */
@Composable
fun TvPlayerScreen(
    setId: String,
    set: MediaSet?,
    run: List<String>,
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
    val upNextShown = upNext.phase != UpNextPhase.HIDDEN

    PlayerLifecycle(viewModel)
    PlayerNavigationEffects(viewModel, setId, run, set?.fsk, onSwitch)
    // The phone's rule: the countdown drops playing (the title has ended)
    // and the wait for the next title's buffer pauses on purpose; neither
    // is a viewer looking away.
    KeepScreenOnWhile(isPlaying = state is PlayerUiState.Playing || upNextShown || upNext.awaitingStart)
    val steps = rememberTvRunSteps(viewModel, setId, run, onSwitch)

    var controlsShown by remember { mutableStateOf(true) }
    var landing by remember { mutableStateOf(TvControlsLanding.PlayPause) }
    var onSeekBar by remember { mutableStateOf(false) }
    // Every press restarts the fade: a viewer working through the
    // controls with the remote is using them, the way a pointer moving
    // over the web's is.
    var presses by remember { mutableIntStateOf(0) }
    // Saved, as on the phone: a configuration change is not a viewer asking
    // for the numbers to go, nor for the list they were filing into to close.
    var statsShown by rememberSaveable { mutableStateOf(false) }
    var choosingList by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    TvPlayerOverlaysReset(setId, marks == null, player == null, closeList = { choosingList = false }, closePanel = { settingsOpen = false })
    TvControlsAutoHide(controlsShown, state, presses, held = choosingList || settingsOpen || upNextShown, onHide = { controlsShown = false })
    // Up with the card and left up after it, as the phone brings its bar
    // back for it; the card counts as shown within the same frame, so the
    // remote lands on it rather than on a picture it is being taken from.
    LaunchedEffect(upNextShown) { if (upNextShown) controlsShown = true }

    val barShown = (controlsShown || upNextShown) && controlsMayShow(state) && player != null
    // Where the bottom controls begin, for the subtitles to clear them.
    var barTop by remember { mutableStateOf<Float?>(null) }
    val root = remember { FocusRequester() }
    val focus = remember { TvPlayerFocus() }
    val remote =
        remember {
            TvPlayerRemote(
                show = { to ->
                    landing = to
                    controlsShown = true
                },
                onNext = { steps.next() },
                onPrevious = { steps.previous() },
            )
        }
    TvRemoteFollowsControls(barShown, settingsOpen, upNextShown, landing, root, focus, busy = { choosingList || onSeekBar })
    TvPlayerBack(
        barShown = barShown,
        onSeekBar = onSeekBar,
        settingsOpen = settingsOpen,
        upNextShown = upNextShown,
        onClosePanel = {
            landing = TvControlsLanding.Settings
            settingsOpen = false
        },
        onCancelUpNext = viewModel::cancelUpNext,
        onHideControls = { controlsShown = false },
        onLeave = onBack,
    )

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) presses++
                    remote.onKey(
                        event,
                        player,
                        controlsShowing = barShown,
                        onSeekBar = onSeekBar,
                        canControl = controlsMayShow(state),
                        panelOpen = settingsOpen,
                        upNextShown = upNextShown,
                    )
                }.focusRequester(root)
                .focusProperties { canFocus = !barShown }
                .focusable()
                .testTag(TvPlayerScreenTag),
        contentAlignment = Alignment.Center,
    ) {
        player?.let { current ->
            TvVideoWithSubtitles(current, subtitleCues, choices, barTop = barTop.takeIf { barShown })
            if (barShown) {
                TvPlayerControlsForViewModel(
                    player = current,
                    set = set,
                    focus = focus,
                    viewModel = viewModel,
                    view = TvControlsView(marks, held, choices.speed, upNext, statsShown),
                    actions =
                        TvControlsActions(
                            onToggleStats = { statsShown = !statsShown },
                            onAddToList = { choosingList = true },
                            onOpenSettings = { settingsOpen = true },
                            onPlayNext = steps.next,
                            onSeekBarFocused = { onSeekBar = it },
                            onBarTopChanged = { barTop = it },
                        ),
                )
            }
            if (settingsOpen) {
                TvPlayerSettingsPanel(choices = choices, viewModel = viewModel, modifier = Modifier.align(Alignment.CenterEnd))
            }
        }
        if (choosingList) {
            TvAddToListOverPlayer(
                marks = marks,
                notice = actionNotice,
                viewModel = viewModel,
                onDismiss = { choosingList = false },
                // Its window's own keys: the remote's media keys still reach
                // the film through it, as through the settings panel.
                keys = { event -> remote.onKey(event, player, controlsShowing = true, onSeekBar = false, canControl = controlsMayShow(state), panelOpen = true) },
            )
        }
        TvActionNotice(
            notice = actionNotice,
            onGone = viewModel::dismissActionNotice,
            modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
        )
        when (val now = state) {
            PlayerUiState.Preparing -> TvLoadingIndicator()
            is PlayerUiState.Failed -> TvCenteredMessage(now.message)
            PlayerUiState.Playing, PlayerUiState.Paused -> Unit
        }
    }
}

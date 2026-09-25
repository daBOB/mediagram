package ui.tv.player

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.input.key.Key
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
import player.controlsMayShow
import player.createListAndAdd
import player.setInList
import player.toggleKids
import player.toggleWatchlist
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
    val marks by viewModel.marks.collectAsStateWithLifecycle()
    val actionNotice by viewModel.actionNotice.collectAsStateWithLifecycle()
    val held by viewModel.held.collectAsStateWithLifecycle()
    val choices by viewModel.choices.collectAsStateWithLifecycle()
    val subtitleCues by viewModel.subtitleCues.collectAsStateWithLifecycle()

    PlayerLifecycle(viewModel)
    // No run: a television has no up-next card yet, and a run is what lets
    // the player count down into the next title — handing it one here would
    // switch episodes under a viewer with nothing on screen to say so or to
    // cancel it. Without one the title simply ends, as it always has here.
    PlayerNavigationEffects(viewModel, setId, run = emptyList(), fsk = set?.fsk, onSwitch = { _, _ -> })
    KeepScreenOnWhile(isPlaying = state is PlayerUiState.Playing)

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
    // Marks go with the title they belong to; a list choice still open when
    // they go would otherwise come back over whatever opens next.
    LaunchedEffect(marks == null) { if (marks == null) choosingList = false }
    // The panel is drawn over a player; without one (a restore that comes
    // back before the player is built) it would be open but nowhere, and
    // still taking the D-pad and Back for itself.
    LaunchedEffect(player == null) { if (player == null) settingsOpen = false }
    TvControlsAutoHide(controlsShown, state, presses, held = choosingList || settingsOpen, onHide = { controlsShown = false })

    val barShown = controlsShown && controlsMayShow(state) && player != null
    // Where the bottom controls begin, for the subtitles to clear them.
    var barTop by remember { mutableStateOf<Float?>(null) }
    val root = remember { FocusRequester() }
    val focus = remember { TvPlayerFocus() }
    val remote =
        remember {
            TvPlayerRemote { to ->
                landing = to
                controlsShown = true
            }
        }
    TvRemoteFollowsControls(barShown, settingsOpen, landing, root, focus)

    // The table's Back row, answered here rather than as a key so a Back
    // that is not one — a gesture, the dispatcher itself — does the same.
    BackHandler {
        when (tvKeyAction(Key.Back, controlsShowing = barShown, focusInControls = onSeekBar, panelOpen = settingsOpen)) {
            TvKeyAction.ClosePanel -> {
                landing = TvControlsLanding.Settings
                settingsOpen = false
            }
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
                    remote.onKey(
                        event,
                        player,
                        controlsShowing = barShown,
                        onSeekBar = onSeekBar,
                        canControl = controlsMayShow(state),
                        panelOpen = settingsOpen,
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
                TvPlayerControls(
                    player = current,
                    set = set,
                    focus = focus,
                    extras =
                        TvPlayerExtras(
                            marks = marks,
                            markActions =
                                TvMarksActions(
                                    onToggleWatchlist = viewModel::toggleWatchlist,
                                    onToggleKids = viewModel::toggleKids,
                                    onAddToList = { choosingList = true },
                                ),
                            statsShown = statsShown,
                            onToggleStats = { statsShown = !statsShown },
                            totals = viewModel.totals,
                            held = held,
                            speed = choices.speed,
                            onOpenSettings = { settingsOpen = true },
                        ),
                    onSeekBarFocused = { onSeekBar = it },
                    onBarTopChanged = { barTop = it },
                )
            }
            if (settingsOpen) {
                TvPlayerSettingsPanel(choices = choices, viewModel = viewModel, modifier = Modifier.align(Alignment.CenterEnd))
            }
        }
        marks?.takeIf { choosingList }?.let { open ->
            TvAddToListDialog(
                lists = open.lists,
                memberOf = open.memberOf,
                onToggle = viewModel::setInList,
                onCreate = viewModel::createListAndAdd,
                onDismiss = { choosingList = false },
                notice = actionNotice,
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

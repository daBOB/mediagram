package ui.tv.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.KeyEvent
import androidx.media3.common.Player
import model.MediaSet
import player.PlayerChoices
import player.PlayerMarksState
import player.PlayerViewModel
import player.UpNextUiState
import player.createListAndAdd
import player.playFromRun
import player.previous
import player.restart
import player.setInList
import player.toggleSubtitles
import player.toggleWatchlist
import ui.tv.setup.LocalTvDialogKeys

/** What the controls read from the screen's own state, beside the player itself. */
internal class TvControlsView(
    val marks: PlayerMarksState?,
    val held: Boolean,
    val upNext: UpNextUiState,
    val statsShown: Boolean,
    /** What the tools read: subtitles, speed, audio and framing. */
    val choices: PlayerChoices,
    /** Whether the run has an episode list to open; ☰ is left out without one. */
    val hasEpisodes: Boolean = false,
    /** Whether the episode list is open. */
    val sidebarOpen: Boolean = false,
)

/** What pressing the controls does to the screen's own state rather than to the player. */
internal class TvControlsActions(
    val onToggleStats: () -> Unit,
    val onAddToList: () -> Unit,
    val onKids: () -> Unit,
    val onOpenMenu: (TvCardMenu) -> Unit,
    /** One menu giving way to another in its place: CC ▾'s "Style…". */
    val onSwitchMenu: (TvCardMenu) -> Unit,
    val onCloseMenu: () -> Unit,
    val onOpenEpisodes: () -> Unit,
    val onCloseEpisodes: () -> Unit,
    val onPickEpisode: (String) -> Unit,
    val onToggleNotes: (() -> Unit)?,
    val onSeekBarFocused: (Boolean) -> Unit,
)

/**
 * What pressing the controls does to the overlay state ([overlays]) and to
 * where the remote goes next. A menu or the episode list remembers the
 * control that opened it ([TvPlayerFocus.opener]) for [closePanel] to
 * return to; a picked episode plays and closes the list ([onPicked]
 * sends the remote to play/pause for the title that follows).
 */
internal fun tvControlsActions(
    overlays: TvPlayerOverlayState,
    focus: TvPlayerFocus,
    viewModel: PlayerViewModel,
    closePanel: () -> Unit,
    onToggleNotes: (() -> Unit)?,
    onSeekBarFocused: (Boolean) -> Unit,
    onPicked: () -> Unit,
) = TvControlsActions(
    onToggleStats = { overlays.statsShown = !overlays.statsShown },
    onAddToList = { overlays.choosingList = true },
    onKids = { overlays.choosingKids = true },
    onOpenMenu = { opened ->
        focus.opener = focus.openerOf(opened)
        overlays.menu = opened
    },
    onSwitchMenu = { overlays.menu = it },
    onCloseMenu = closePanel,
    onOpenEpisodes = {
        focus.opener = focus.episodes
        overlays.sidebarOpen = true
    },
    onCloseEpisodes = closePanel,
    onPickEpisode = { id ->
        onPicked()
        overlays.sidebarOpen = false
        viewModel.playFromRun(id)
    },
    onToggleNotes = onToggleNotes,
    onSeekBarFocused = onSeekBarFocused,
)

/**
 * [TvPlayerControls] over the shared ViewModel: the marks and statistics,
 * the tools and the transport, ⏮ and ⏭ included. The up-next card is not
 * among them but floats over the stage ([TvPlayerStage]); its Play now is
 * the same step forward as "Play next", its Cancel the ViewModel's own,
 * which leaves that standing button in place.
 */
@Composable
internal fun TvPlayerControlsForViewModel(
    player: Player,
    set: MediaSet?,
    focus: TvPlayerFocus,
    viewModel: PlayerViewModel,
    view: TvControlsView,
    actions: TvControlsActions,
    bands: TvStageBands,
) {
    TvPlayerControls(
        player = player,
        set = set,
        focus = focus,
        extras =
            TvPlayerExtras(
                marks = view.marks,
                markActions =
                    TvMarksActions(
                        onToggleWatchlist = viewModel::toggleWatchlist,
                        onKids = actions.onKids,
                        onAddToList = actions.onAddToList,
                    ),
                statsShown = view.statsShown,
                onToggleStats = actions.onToggleStats,
                totals = viewModel.totals,
                held = view.held,
                choices = view.choices,
                onToggleSubtitles = viewModel::toggleSubtitles,
                onOpenMenu = actions.onOpenMenu,
                upNext = view.upNext,
                onRestart = viewModel::restart,
                onPrevious = viewModel::previous,
                onPlayNext = viewModel::playNext,
                onOpenEpisodes = actions.onOpenEpisodes.takeIf { view.hasEpisodes },
                onToggleNotes = actions.onToggleNotes,
                sidebarOpen = view.sidebarOpen,
            ),
        onSeekBarFocused = actions.onSeekBarFocused,
        bands = bands,
    )
}

/**
 * The list dialog over the film, for [marks] while they are there. Its
 * window takes every key, so [keys] is offered each one first — the
 * player's own remote, so a media key pressed while filing a title does
 * what it does everywhere else in the player rather than falling through
 * to the playback session (see [LocalTvDialogKeys]).
 */
@Composable
internal fun TvAddToListOverPlayer(
    marks: PlayerMarksState?,
    notice: String?,
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    keys: (KeyEvent) -> Boolean,
) {
    val open = marks ?: return
    CompositionLocalProvider(LocalTvDialogKeys provides keys) {
        TvAddToListDialog(
            lists = open.lists,
            memberOf = open.memberOf,
            onToggle = viewModel::setInList,
            onCreate = viewModel::createListAndAdd,
            onDismiss = onDismiss,
            notice = notice,
        )
    }
}

/** The list and Kids dialogs over the film, while they are open; [keys] is offered each of their keys first. */
@Composable
internal fun TvMarkDialogs(
    overlays: TvPlayerOverlayState,
    marks: PlayerMarksState?,
    notice: String?,
    viewModel: PlayerViewModel,
    keys: (KeyEvent) -> Boolean,
) {
    if (overlays.choosingList) {
        TvAddToListOverPlayer(marks = marks, notice = notice, viewModel = viewModel, onDismiss = { overlays.choosingList = false }, keys = keys)
    }
    if (overlays.choosingKids) TvKidsChoiceOverPlayer(marks = marks, viewModel = viewModel, onDismiss = { overlays.choosingKids = false }, keys = keys)
}

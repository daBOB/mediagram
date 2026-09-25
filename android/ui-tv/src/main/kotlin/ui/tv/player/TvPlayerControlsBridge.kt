package ui.tv.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import androidx.media3.common.Player
import model.MediaSet
import player.PlayerMarksState
import player.PlayerViewModel
import player.UpNextUiState
import player.createListAndAdd
import player.setInList
import player.toggleKids
import player.toggleWatchlist
import ui.tv.setup.LocalTvDialogKeys

/** What the controls read from the screen's own state, beside the player itself. */
internal class TvControlsView(
    val marks: PlayerMarksState?,
    val held: Boolean,
    val speed: Float,
    val upNext: UpNextUiState,
    val statsShown: Boolean,
)

/** What pressing the controls does to the screen's own state rather than to the player. */
internal class TvControlsActions(
    val onToggleStats: () -> Unit,
    val onAddToList: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onPlayNext: () -> Unit,
    val onSeekBarFocused: (Boolean) -> Unit,
    val onBarTopChanged: (Float) -> Unit,
)

/**
 * [TvPlayerControls] over the shared ViewModel: the marks and statistics,
 * the settings gear, the standing "Play next", and the up-next card above
 * the clock — its Play now is the same step forward as "Play next", its
 * Cancel the ViewModel's own, which leaves that standing button in place.
 */
@Composable
internal fun TvPlayerControlsForViewModel(
    player: Player,
    set: MediaSet?,
    focus: TvPlayerFocus,
    viewModel: PlayerViewModel,
    view: TvControlsView,
    actions: TvControlsActions,
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
                        onToggleKids = viewModel::toggleKids,
                        onAddToList = actions.onAddToList,
                    ),
                statsShown = view.statsShown,
                onToggleStats = actions.onToggleStats,
                totals = viewModel.totals,
                held = view.held,
                speed = view.speed,
                onOpenSettings = actions.onOpenSettings,
                hasNext = view.upNext.hasNext,
                nextTitleLine = view.upNext.titleLine,
                onPlayNext = actions.onPlayNext,
            ),
        onSeekBarFocused = actions.onSeekBarFocused,
        onBarTopChanged = actions.onBarTopChanged,
    ) {
        TvUpNextCard(
            state = view.upNext,
            playNow = focus.upNext,
            onPlayNow = actions.onPlayNext,
            onCancel = viewModel::cancelUpNext,
            modifier = Modifier.align(Alignment.End),
        )
    }
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

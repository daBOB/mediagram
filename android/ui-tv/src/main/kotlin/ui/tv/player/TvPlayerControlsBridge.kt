package ui.tv.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.media3.common.Player
import model.MediaSet
import player.PlayerMarksState
import player.PlayerViewModel
import player.UpNextUiState
import player.toggleKids
import player.toggleWatchlist

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

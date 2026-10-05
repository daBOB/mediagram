// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import player.EpisodeList
import player.PlayerChoices
import player.PlayerViewModel
import player.UpNextUiState
import player.playFromRun

/**
 * The card and the menu that opens above it, as the screen lays them out —
 * split out of `PlayerScreen` to keep that file under the project's line
 * guideline. While the episode sidebar stands beside the picture the card is
 * laid out in the width it leaves, so the two never overlap: it ends short of
 * the sidebar by its own margin, centred in what remains, and goes back to
 * the window's width when the sidebar closes.
 */
@Composable
internal fun BoxScope.PlayerCardLayer(
    player: Player,
    viewModel: PlayerViewModel,
    card: PlayerCardState,
    choices: PlayerChoices,
    upNext: UpNextUiState,
    statsShown: Boolean,
    hasEpisodes: Boolean,
    catalogedDurationSecs: Int?,
    onToggleStats: () -> Unit,
    onEnterPip: (() -> Unit)?,
    onScrubbingChanged: (Boolean) -> Unit,
) {
    val clearance = if (card.sidebarOpen && !sidebarCoversScreen()) SIDEBAR_WIDTH else 0.dp
    Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(end = clearance)) {
        PlayerControlCard(
            player = player,
            view =
                PlayerCardView(
                    choices, upNext, statsShown, hasEpisodes = hasEpisodes, catalogedDurationSecs = catalogedDurationSecs,
                    openMenu = card.menu, sidebarOpen = card.sidebarOpen,
                ),
            actions = playerCardActions(viewModel, card, onToggleStats = onToggleStats, onEnterPip = onEnterPip),
            onScrubbingChanged = onScrubbingChanged,
            onBounds = { card.bounds = it },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
    CardMenuOverStage(card, choices, viewModel.cardMenuActions())
}

/** The run's sidebar over the picture, which keeps playing beside it; a pick plays that title and puts the sidebar away. */
@Composable
internal fun BoxScope.PlayerEpisodeSidebar(
    list: EpisodeList,
    card: PlayerCardState,
    viewModel: PlayerViewModel,
) {
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

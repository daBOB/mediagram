package ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import player.PlayerViewModel
import player.controlsMayShow
import player.titleLine
import ui.catalog.rememberOpenCastSettings

/** The remote's label while this device plays to a wireless display; not in `cast.json`, since the web has no second screen. */
internal fun playingOn(name: String) = "Playing on $name"

/**
 * The player while a wireless display shows its picture: the picture and its
 * subtitles go full screen to the display called [name] ([PresentOnDisplay]),
 * and this screen takes the TV box remote's look ([RemoteFrame]), driving
 * its own player. As on that remote the card does not take itself away,
 * with nothing on this screen for it to uncover, and it holds what that card
 * holds, without Stop: Back leaves the player as always. Its Play on TV
 * names the display and opens the system's Cast screen, where the display
 * is let go. A title opening or failed is said here ([PlayerStageNote]), the
 * remote in the hand. No picture-in-picture either; [PlayerScreen] draws
 * none while this stands.
 */
@Composable
internal fun PresentingPlayer(
    name: String,
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val report by viewModel.report.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val openSet by viewModel.openSet.collectAsStateWithLifecycle()
    val choices by viewModel.choices.collectAsStateWithLifecycle()
    val cues by viewModel.subtitleCues.collectAsStateWithLifecycle()
    val upNext by viewModel.upNext.collectAsStateWithLifecycle()
    val together by viewModel.together.collectAsStateWithLifecycle()
    val openCastScreen = rememberOpenCastSettings()
    player?.let { current ->
        PresentOnDisplay(name) { VideoWithSubtitles(current, cues, choices, barTop = null, isInPip = false, onPictureBottomChanged = {}) }
    }
    val backdrop = openSet?.let { it.backdropPath ?: it.posterPath }
    RemoteFrame(title = titleLine(openSet), backdrop = backdrop, label = playingOn(name), notice = together.notice, onBack = onBack) { card ->
        // As on the player's own look: opening or failed, there is nothing for the card to drive, nor a menu to keep open.
        val cardShown = controlsMayShow(state)
        LaunchedEffect(cardShown) { if (!cardShown) card.closeMenu() }
        player?.takeIf { cardShown }?.let { current ->
            PlayerControlCard(
                player = current,
                view = PlayerCardView(choices, upNext, statsShown = false, hasEpisodes = false, catalogedDurationSecs = openSet?.durationSecs, openMenu = card.menu, presentingOn = name),
                actions = playerCardActions(viewModel, card, onToggleStats = {}, onEnterPip = null, onPlayOnTv = openCastScreen, drivesTv = true),
                onScrubbingChanged = {},
                modifier = Modifier.align(Alignment.BottomCenter),
                onBounds = { card.bounds = it },
            )
            CardMenuOverStage(card, choices, viewModel.cardMenuActions())
        }
        PlayerStageNote(state, report, viewModel)
    }
}

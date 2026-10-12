// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import designsystem.Motion
import player.EpisodeList
import player.PlayerChoices
import player.PlayerViewModel
import player.UpNextUiState
import player.playFromRun
import player.together.TogetherUi

/** The web's controls fade: opacity over 260ms on CSS `ease` (`playback.css:41`), shared by the card and the top bar. */
internal val HudFade: TweenSpec<Float> = tween(260, easing = Motion.CssEase)

/**
 * The card and the menu that opens above it, as the screen lays them out.
 * While the episode sidebar stands beside the picture the card is
 * laid out in the width it leaves, so the two never overlap: it ends short of
 * the sidebar by its own margin, centred in what remains, and goes back to
 * the window's width when the sidebar closes.
 *
 * [shown] fades the card in and out ([HudFade]). While it fades out, taps
 * meant for it go to the picture instead ([TapsReachThePicture]). Its menus
 * are shown and hidden, as the web's are. In picture-in-picture it is not
 * drawn at all: that window has no touch surface of this app's own.
 */
@Composable
internal fun BoxScope.PlayerCardLayer(
    shown: Boolean,
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
    together: TogetherUi,
) {
    if (LocalIsInPictureInPicture.current) return
    val clearance = if (card.sidebarOpen && !sidebarCoversScreen()) SIDEBAR_WIDTH else 0.dp
    AnimatedVisibility(
        visible = shown,
        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(end = clearance),
        enter = fadeIn(HudFade),
        exit = fadeOut(HudFade),
        label = "player card",
    ) {
        // The bounds the subtitles and the up-next card clear stay the card's
        // until it has finished fading out, then go with it.
        DisposableEffect(Unit) { onDispose { card.bounds = null } }
        Box(modifier = Modifier.fillMaxWidth()) {
            PlayerControlCard(
                player = player,
                view =
                    PlayerCardView(
                        choices, upNext, statsShown, hasEpisodes = hasEpisodes, catalogedDurationSecs = catalogedDurationSecs,
                        openMenu = card.menu, sidebarOpen = card.sidebarOpen, together = together,
                    ),
                actions = playerCardActions(viewModel, card, onToggleStats = onToggleStats, onEnterPip = onEnterPip),
                onScrubbingChanged = onScrubbingChanged,
                onBounds = { card.bounds = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            if (!shown) TapsReachThePicture()
        }
    }
    if (shown) CardMenuOverStage(card, choices, viewModel.cardMenuActions())
}

/**
 * Laid over controls while they fade out. It is hit first, so the
 * half-hidden control under it never takes the tap; it consumes nothing, so
 * the tap goes on to the picture behind, which brings the controls back. The
 * web's `pointer-events: none` on a resting HUD (`playback.css:63-70`,
 * `player-card.css:32`).
 */
@Composable
internal fun BoxScope.TapsReachThePicture() {
    Box(
        Modifier
            .matchParentSize()
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
    )
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

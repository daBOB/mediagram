package ui.player

import player.PlayerViewModel
import player.chooseAudioTrack
import player.chooseFraming
import player.chooseSubtitleLanguage
import player.nudgeSubtitleOffset
import player.previous
import player.resetSubtitleOffset
import player.restart
import player.setSpeed
import player.setSubtitleBacking
import player.setSubtitleSize
import player.toggleSubtitles

/**
 * The card's and its menus' actions over [PlayerViewModel] and the card's
 * own [PlayerCardState] — split out of `PlayerScreen` to keep it under the
 * project's line guideline; the card and its menus stay plain functions of
 * what they are handed, with no `PlayerViewModel` of their own.
 */
internal fun playerCardActions(
    viewModel: PlayerViewModel,
    card: PlayerCardState,
    onToggleStats: () -> Unit,
    onEnterPip: (() -> Unit)?,
): PlayerCardActions =
    PlayerCardActions(
        onRestart = viewModel::restart,
        onPrevious = viewModel::previous,
        onNext = viewModel::playNext,
        onToggleSubtitles = viewModel::toggleSubtitles,
        onOpenMenu = card::toggle,
        onToggleStats = onToggleStats,
        onEpisodes = card::toggleSidebar,
        onEnterPip = onEnterPip,
        onAnchor = card::anchor,
        focusOf = card::focusOf,
    )

/** Every write a card menu makes — kept as the one place that names them. */
internal fun PlayerViewModel.cardMenuActions(): CardMenuActions =
    CardMenuActions(
        onSpeed = this::setSpeed,
        onAudio = this::chooseAudioTrack,
        onSubtitle = this::chooseSubtitleLanguage,
        onSize = this::setSubtitleSize,
        onBacking = this::setSubtitleBacking,
        onNudge = this::nudgeSubtitleOffset,
        onResetOffset = this::resetSubtitleOffset,
        onFraming = this::chooseFraming,
    )

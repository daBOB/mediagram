package ui.player

import player.PlayerViewModel
import player.chooseAudioTrack
import player.createListAndAdd
import player.chooseFraming
import player.chooseSubtitleLanguage
import player.nudgeSubtitleOffset
import player.onTogetherButton
import player.previous
import player.resetSubtitleOffset
import player.restart
import player.setInList
import player.setKidsMark
import player.setSpeed
import player.setSubtitleBacking
import player.setSubtitleSize
import player.toggleSubtitles
import player.toggleWatchlist

/**
 * The card's and its menus' actions over [PlayerViewModel] and the card's
 * own [PlayerCardState]; the card and its menus stay plain functions of
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
        onTogether = viewModel::onTogetherButton,
    )

/** What the top bar's marks write: My List, Kids and the lists. */
internal fun PlayerViewModel.marksActions(): PlayerMarksActions =
    PlayerMarksActions(
        onToggleWatchlist = this::toggleWatchlist,
        onKidsMark = this::setKidsMark,
        onSetInList = this::setInList,
        onCreateList = this::createListAndAdd,
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

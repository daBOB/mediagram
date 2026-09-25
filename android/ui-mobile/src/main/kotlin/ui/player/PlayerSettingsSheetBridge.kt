package ui.player

import androidx.compose.runtime.Composable
import player.PlayerChoices
import player.PlayerViewModel
import player.chooseAudioTrack
import player.chooseFraming
import player.chooseSubtitleLanguage
import player.nudgeSubtitleOffset
import player.resetSubtitleOffset
import player.setSpeed
import player.setSubtitleBacking
import player.setSubtitleSize

/**
 * Wires [PlayerViewModel]'s subtitle methods onto [PlayerSettingsSheet] —
 * split out of `PlayerScreen` to keep that file under the project's line
 * guideline; the sheet itself stays a plain function of its parameters,
 * with no `PlayerViewModel` of its own.
 */
@Composable
internal fun PlayerSettingsSheetForViewModel(
    choices: PlayerChoices,
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit,
) {
    PlayerSettingsSheet(
        currentSpeed = choices.speed,
        onSpeedChosen = viewModel::setSpeed,
        audioOptions = choices.audioOptions,
        onAudioChosen = viewModel::chooseAudioTrack,
        subtitleOptions = choices.subtitleOptions,
        onSubtitleChosen = viewModel::chooseSubtitleLanguage,
        subtitleSizePercent = choices.subtitleSizePercent,
        onSubtitleSizeChosen = viewModel::setSubtitleSize,
        subtitleBacking = choices.subtitleBacking,
        onSubtitleBackingChosen = viewModel::setSubtitleBacking,
        subtitleOffsetMs = choices.subtitleOffsetMs,
        onSubtitleNudge = viewModel::nudgeSubtitleOffset,
        onSubtitleOffsetReset = viewModel::resetSubtitleOffset,
        framing = choices.framing,
        onFramingChosen = viewModel::chooseFraming,
        onDismiss = onDismiss,
    )
}

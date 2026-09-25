package ui.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import designsystem.Overscan
import designsystem.Palette
import designsystem.Spacing
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

/** Finds the settings panel in a test. */
internal const val TvSettingsPanelTag = "tv-player-settings"

/**
 * The phone's settings sheet as a panel down the right-hand side of the
 * picture, which the transport's gear opens: Speed, Audio, Subtitles,
 * Subtitle style and Framing, in the phone's order, on the phone's terms —
 * Audio only for a title with more than one track, the two subtitle
 * sections only for a file that carries subtitles at all.
 *
 * To one side rather than over the middle, and not dimming the rest, so a
 * subtitle size or a sync nudge can be judged against the film it applies
 * to while the panel is still open.
 *
 * The remote lands on its first row the moment it opens, and cannot wander
 * out of it: the transport behind it is still drawn, and a Left meant for
 * the next choice would otherwise land on a button that skips the film.
 * Back closes it (the player screen answers that, from the key table).
 */
@Composable
internal fun TvPlayerSettingsPanel(
    choices: PlayerChoices,
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }

    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .width(PanelWidth)
                .background(Palette.Page)
                .testTag(TvSettingsPanelTag)
                .focusProperties { onExit = { cancelFocusChange() } }
                .focusGroup()
                .verticalScroll(rememberScrollState())
                // Inside the overscan margin on the edge it meets; the side
                // facing the picture needs only breathing room.
                .padding(start = Spacing.large, end = Overscan.horizontal, top = Overscan.vertical, bottom = Overscan.vertical),
    ) {
        TvSpeedSection(speed = choices.speed, onChosen = viewModel::setSpeed, first = first)
        if (choices.audioOptions.isNotEmpty()) {
            TvAudioSection(options = choices.audioOptions, onChosen = viewModel::chooseAudioTrack)
        }
        if (choices.subtitleOptions.isNotEmpty()) {
            TvSubtitleSection(options = choices.subtitleOptions, onChosen = viewModel::chooseSubtitleLanguage)
            TvSubtitleStyleSection(
                sizePercent = choices.subtitleSizePercent,
                onSizeChosen = viewModel::setSubtitleSize,
                backing = choices.subtitleBacking,
                onBackingChosen = viewModel::setSubtitleBacking,
                offsetMs = choices.subtitleOffsetMs,
                onNudge = viewModel::nudgeSubtitleOffset,
                onResetOffset = viewModel::resetSubtitleOffset,
            )
        }
        TvFramingSection(framing = choices.framing, onChosen = viewModel::chooseFraming)
    }
}

/** A third of a 960dp television: room for the longest audio label, most of the picture still in view. */
private val PanelWidth = 340.dp

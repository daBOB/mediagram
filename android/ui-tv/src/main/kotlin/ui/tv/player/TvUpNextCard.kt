package ui.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import designsystem.Palette
import designsystem.Spacing
import designsystem.TvTypeScale
import player.UpNextPhase
import player.UpNextUiState

/** Finds the card in a test. */
internal const val TvUpNextCardTag = "tv-up-next-card"

/**
 * The phone's `UpNextCard` for a television: "Up next" and the next
 * title's own line, the countdown once this one has ended, and the same
 * two answers — Play now and Cancel — as buttons in the controls' own
 * treatment, so the remote reads them as part of the same set.
 *
 * [playNow] is how the screen puts the remote on Play now when the card
 * appears; the card itself never takes focus, because only the screen
 * knows whether a viewer is busy in the settings panel or the list dialog
 * at that moment (see [TvRemoteFollowsControls]).
 */
@Composable
internal fun TvUpNextCard(
    state: UpNextUiState,
    playNow: FocusRequester,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.phase == UpNextPhase.HIDDEN) return
    Column(
        modifier =
            modifier
                .widthIn(max = CARD_MAX_WIDTH)
                // Opaque, unlike the scrims: it floats over the film itself,
                // and a countdown read through a bright scene is not read.
                .background(Palette.Page)
                .padding(Spacing.medium)
                .testTag(TvUpNextCardTag),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        // "Up next" and the countdown on one line, and the title in the
        // body's size: the card floats between the title along the top and
        // the controls along the bottom, and on a 540dp-high television a
        // heading-sized line more would reach the one or the other.
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            Text(text = "Up next", style = TvTypeScale.body, color = Palette.Figures)
            Text(
                text =
                    if (state.phase == UpNextPhase.COUNTING) {
                        "Starting in ${state.countdownSecondsLeft ?: 0}…"
                    } else {
                        "When this ends"
                    },
                style = TvTypeScale.body,
                color = Palette.Text,
            )
        }
        if (state.titleLine.isNotEmpty()) {
            Text(text = state.titleLine, style = TvTypeScale.body.copy(fontWeight = FontWeight.SemiBold), color = Palette.Text)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            TvOverlayButton(
                text = "Play now",
                style = TvTypeScale.body,
                enabled = true,
                onClick = onPlayNow,
                modifier = Modifier.focusRequester(playNow),
            )
            TvOverlayButton(text = "Cancel", style = TvTypeScale.body, enabled = true, onClick = onCancel)
        }
    }
}

/** Wide enough for a long episode title on two lines, narrow enough to leave most of the picture alone. */
private val CARD_MAX_WIDTH = 480.dp

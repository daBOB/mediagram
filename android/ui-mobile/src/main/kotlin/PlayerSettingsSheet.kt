package ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import designsystem.Spacing
import playback.AudioOption
import player.PLAYBACK_SPEEDS
import player.speedLabel

/**
 * The settings sheet the control bar's gear opens. Speed and Audio are the
 * sections built so far; Subtitles and Framing join them beside it, in the
 * same sheet rather than one each — a viewer reaching for one setting
 * mid-film should not have to remember which button opens which.
 *
 * [audioOptions] is empty for a title with only one audio track (or before
 * the file's own tracks and any remembered choice have both resolved),
 * which is exactly when the Audio section stays off the sheet entirely.
 *
 * Scrolls (more sections than fit a short landscape sheet is routine once
 * Audio joins Speed) and pads for the navigation bar itself, rather than
 * trusting `ModalBottomSheet`'s own default insets — a phone with
 * three-button navigation otherwise sits its home/back row over the last
 * item, on top of the row rather than below it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsSheet(
    currentSpeed: Float,
    onSpeedChosen: (Float) -> Unit,
    audioOptions: List<AudioOption>,
    onAudioChosen: (AudioOption) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(Spacing.medium),
        ) {
            Text("Speed", style = MaterialTheme.typography.titleMedium)
            for (speed in PLAYBACK_SPEEDS) {
                SpeedRow(speed = speed, selected = speed == currentSpeed, onClick = { onSpeedChosen(speed) })
            }
            if (audioOptions.isNotEmpty()) {
                AudioSection(options = audioOptions, onChosen = onAudioChosen)
            }
        }
    }
}

@Composable
private fun SpeedRow(speed: Float, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(speedLabel(speed), modifier = Modifier.padding(start = Spacing.small))
    }
}

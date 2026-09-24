package ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
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
import player.PLAYBACK_SPEEDS
import player.speedLabel

/**
 * The settings sheet the control bar's gear opens. Speed is the only
 * section this phase adds; 05/06/08 add Audio, Subtitles and Framing
 * beside it, in the same sheet rather than one each — a viewer reaching
 * for one setting mid-film should not have to remember which button opens
 * which.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsSheet(currentSpeed: Float, onSpeedChosen: (Float) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.medium)) {
            Text("Speed", style = MaterialTheme.typography.titleMedium)
            for (speed in PLAYBACK_SPEEDS) {
                SpeedRow(speed = speed, selected = speed == currentSpeed, onClick = { onSpeedChosen(speed) })
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

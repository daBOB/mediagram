package ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
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
 * The Speed menu's rows: every one of [PLAYBACK_SPEEDS], the current one
 * marked — the same radio-row shape [AudioSection] and [FramingSection] use.
 */
@Composable
internal fun SpeedSection(
    speed: Float,
    onChosen: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Speed", style = MaterialTheme.typography.titleMedium)
        for (option in PLAYBACK_SPEEDS) {
            SpeedRow(speed = option, selected = option == speed, onClick = { onChosen(option) })
        }
    }
}

@Composable
private fun SpeedRow(
    speed: Float,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .heightIn(min = MIN_TARGET)
                .padding(vertical = Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(speedLabel(speed), modifier = Modifier.padding(start = Spacing.small))
    }
}

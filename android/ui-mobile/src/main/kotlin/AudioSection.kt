package ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import playback.AudioOption

/**
 * The settings sheet's "Audio" section — rows already built and labelled by
 * `playback.audioOptions`, so this is only the same radio-row shape the
 * sheet's other sections use. Its caller is what decides whether to show
 * it at all: a single track is not a menu, it is a label for something
 * nobody can change, and this never sees that case since the list arrives
 * empty for it.
 */
@Composable
fun AudioSection(options: List<AudioOption>, onChosen: (AudioOption) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.medium)) {
        Text("Audio", style = MaterialTheme.typography.titleMedium)
        for (option in options) {
            AudioOptionRow(option = option, onClick = { onChosen(option) })
        }
    }
}

@Composable
private fun AudioOptionRow(option: AudioOption, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = option.selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = option.selected, onClick = null)
        Text(option.text, modifier = Modifier.padding(start = Spacing.small))
    }
}

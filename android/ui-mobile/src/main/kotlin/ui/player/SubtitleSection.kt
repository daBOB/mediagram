package ui.player

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
import player.SubtitleOption

/**
 * The settings sheet's "Subtitles" section: "Off" plus a row per language —
 * built and labelled by `player.subtitleOptions`, the same radio-row shape
 * [AudioSection] uses. Its caller decides whether to show it at all: empty
 * for a file with no subtitles, which this never sees since the list
 * arrives empty for it.
 */
@Composable
fun SubtitleSection(options: List<SubtitleOption>, onChosen: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.medium)) {
        Text("Subtitles", style = MaterialTheme.typography.titleMedium)
        for (option in options) {
            SubtitleOptionRow(option = option, onClick = { onChosen(option.value) })
        }
    }
}

@Composable
private fun SubtitleOptionRow(option: SubtitleOption, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = option.selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = option.selected, onClick = null)
        Text(option.label, modifier = Modifier.padding(start = Spacing.small))
    }
}

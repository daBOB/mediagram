package ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import designsystem.Spacing
import playback.Framing

/**
 * The Framing menu's rows — the four rows the web's `z` key
 * cycles through (`framing.js`), offered directly here since this app has
 * no keyboard to cycle with. Always offered, unlike Audio and
 * Subtitles: every title has a shape, not just the ones with extra tracks.
 * A pinch over the picture reaches Fill and Fit, the two of these a viewer
 * asks for most, without opening the menu at all — see `PlayerGestures`.
 */
@Composable
fun FramingSection(
    framing: Framing,
    onChosen: (Framing) -> Unit,
    /** The Deinterlace row's state, or null to leave the row out (an HDR title, or a remote with no picture of its own). */
    deinterlace: Boolean? = null,
    onDeinterlace: (Boolean) -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.medium)) {
        Text("Framing", style = MaterialTheme.typography.titleMedium)
        for (option in Framing.entries) {
            FramingRow(option = option, selected = option == framing, onClick = { onChosen(option) })
        }
        if (deinterlace != null) DeinterlaceRow(on = deinterlace, onChange = onDeinterlace)
    }
}

/** For interlaced video flagged progressive, which shows comb lines on motion; remembered per show. */
@Composable
private fun DeinterlaceRow(on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = MIN_TARGET)
            .padding(vertical = Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Deinterlace", modifier = Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = null)
    }
}

@Composable
private fun FramingRow(option: Framing, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = MIN_TARGET)
            .padding(vertical = Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(option.label, modifier = Modifier.padding(start = Spacing.small))
    }
}

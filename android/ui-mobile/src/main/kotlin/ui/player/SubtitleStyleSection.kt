package ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import designsystem.Spacing
import playback.CUE_BACKINGS
import playback.CUE_SIZES
import playback.cueOffsetLabel

/**
 * The settings sheet's "Subtitle style" section: size, backing and a timing
 * nudge — a port of the web's subtitle-appearance panel
 * (`subtitle-panel.js`), with no position control; see that file's own
 * header for why one was tried and dropped. Its caller decides whether to
 * show this at all, same as [SubtitleSection].
 */
@Composable
fun SubtitleStyleSection(
    sizePercent: Int,
    onSizeChosen: (Int) -> Unit,
    backing: String,
    onBackingChosen: (String) -> Unit,
    offsetMs: Long,
    onNudge: (Int) -> Unit,
    onResetOffset: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.medium)) {
        Text("Subtitle style", style = MaterialTheme.typography.titleMedium)
        for (option in CUE_SIZES) {
            ChoiceRow(label = option.label, selected = option.percent == sizePercent, onClick = { onSizeChosen(option.percent) })
        }
        for (option in CUE_BACKINGS) {
            ChoiceRow(label = option.label, selected = option.stored == backing, onClick = { onBackingChosen(option.stored) })
        }
        SyncRow(offsetMs = offsetMs, onNudge = onNudge, onResetOffset = onResetOffset)
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = Spacing.small))
    }
}

@Composable
private fun SyncRow(offsetMs: Long, onNudge: (Int) -> Unit, onResetOffset: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Sync", modifier = Modifier.padding(end = Spacing.small))
        GlyphButton(glyph = "−", description = "Subtitles earlier", enabled = true, onClick = { onNudge(-1) })
        Text(cueOffsetLabel(offsetMs / 1000.0), modifier = Modifier.padding(horizontal = Spacing.small))
        GlyphButton(glyph = "+", description = "Subtitles later", enabled = true, onClick = { onNudge(1) })
        TextButton(onClick = onResetOffset) { Text("Reset") }
    }
}

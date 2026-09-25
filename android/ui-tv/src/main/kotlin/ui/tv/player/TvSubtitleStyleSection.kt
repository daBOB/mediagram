package ui.tv.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.tv.material3.Text
import designsystem.Palette
import designsystem.Spacing
import designsystem.TvTypeScale
import playback.CUE_BACKINGS
import playback.CUE_SIZES
import playback.cueOffsetLabel

/**
 * The settings panel's "Subtitle style": the phone's size, backing and
 * timing nudge, with the phone's labels. A size here is the same percentage
 * the phone offers, applied on top of the television's own larger cue
 * ([TvSubtitles]), so "Large" is the same step up on both screens rather
 * than the same number of points.
 *
 * The sync row is the one row of the panel that runs sideways: earlier,
 * the offset as it stands, later, and back to none — Left and Right move
 * between them, as they do between the transport's buttons.
 */
@Composable
internal fun TvSubtitleStyleSection(
    sizePercent: Int,
    onSizeChosen: (Int) -> Unit,
    backing: String,
    onBackingChosen: (String) -> Unit,
    offsetMs: Long,
    onNudge: (Int) -> Unit,
    onResetOffset: () -> Unit,
) {
    TvSettingsHeading("Subtitle style")
    for (option in CUE_SIZES) {
        TvChoiceRow(label = option.label, selected = option.percent == sizePercent, onClick = { onSizeChosen(option.percent) })
    }
    for (option in CUE_BACKINGS) {
        TvChoiceRow(label = option.label, selected = option.stored == backing, onClick = { onBackingChosen(option.stored) })
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.small),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Sync", style = TvTypeScale.body, color = Palette.Figures)
        SyncButton(text = "−", description = "Subtitles earlier", onClick = { onNudge(-1) })
        Text(text = cueOffsetLabel(offsetMs / 1000.0), style = TvTypeScale.body, color = Palette.Text)
        SyncButton(text = "+", description = "Subtitles later", onClick = { onNudge(1) })
        SyncButton(text = "Reset", description = null, onClick = onResetOffset)
    }
}

/** A sync control, named for a screen reader where its glyph says nothing of which way it moves the text. */
@Composable
private fun SyncButton(
    text: String,
    description: String?,
    onClick: () -> Unit,
) {
    TvOverlayButton(
        text = text,
        style = TvTypeScale.body,
        enabled = true,
        onClick = onClick,
        modifier = if (description != null) Modifier.semantics { contentDescription = description } else Modifier,
    )
}

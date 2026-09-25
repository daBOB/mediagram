package ui.tv.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.tv.material3.Text
import designsystem.Palette
import designsystem.Spacing
import designsystem.TvTypeScale
import playback.AudioOption
import playback.Framing
import player.PLAYBACK_SPEEDS
import player.SubtitleOption
import player.speedLabel

/**
 * The settings panel's radio-choice sections — Speed, Audio, Subtitles and
 * Framing, with the phone sheet's own headings, rows and labels in the
 * phone sheet's own order. Each is a plain function of what it shows and
 * what choosing does, as the phone's are; whether Audio and Subtitles
 * appear at all is the panel's decision, on the phone's terms.
 */
@Composable
internal fun TvSpeedSection(
    speed: Float,
    onChosen: (Float) -> Unit,
    current: FocusRequester,
) {
    TvSettingsHeading("Speed")
    // A speed off the list (none today) still gives the remote a row.
    val landing = PLAYBACK_SPEEDS.firstOrNull { it == speed } ?: PLAYBACK_SPEEDS.first()
    for (option in PLAYBACK_SPEEDS) {
        TvChoiceRow(
            label = speedLabel(option),
            selected = option == speed,
            onClick = { onChosen(option) },
            focusRequester = current.takeIf { option == landing },
        )
    }
}

@Composable
internal fun TvAudioSection(
    options: List<AudioOption>,
    onChosen: (AudioOption) -> Unit,
) {
    TvSettingsHeading("Audio")
    for (option in options) {
        TvChoiceRow(label = option.text, selected = option.selected, onClick = { onChosen(option) })
    }
}

@Composable
internal fun TvSubtitleSection(
    options: List<SubtitleOption>,
    onChosen: (String) -> Unit,
) {
    TvSettingsHeading("Subtitles")
    for (option in options) {
        TvChoiceRow(label = option.label, selected = option.selected, onClick = { onChosen(option.value) })
    }
}

@Composable
internal fun TvFramingSection(
    framing: Framing,
    onChosen: (Framing) -> Unit,
) {
    TvSettingsHeading("Framing")
    for (option in Framing.entries) {
        TvChoiceRow(label = option.label, selected = option == framing, onClick = { onChosen(option) })
    }
}

/** A section's name: quieter than its rows, since it is read once and never pressed. */
@Composable
internal fun TvSettingsHeading(text: String) {
    Text(
        text = text,
        style = TvTypeScale.body,
        color = Palette.Figures,
        modifier = Modifier.padding(top = Spacing.medium, bottom = Spacing.extraSmall),
    )
}

/**
 * One choice among its section's, the current one marked — the phone's
 * radio row, drawn as a filled or an empty circle beside the label, in the
 * same focus treatment as every other control over the picture so the
 * remote reads as clearly here as on the transport.
 */
@Composable
internal fun TvChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    TvOverlaySurface(
        onClick = onClick,
        enabled = true,
        modifier =
            Modifier
                .fillMaxWidth()
                .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
                .semantics {
                    this.selected = selected
                    role = Role.RadioButton
                },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.extraSmall),
            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The mark is the selected state drawn, which the row already
            // says as a radio button; read aloud it would be noise.
            Text(text = if (selected) CHOSEN else NOT_CHOSEN, style = TvTypeScale.body, modifier = Modifier.clearAndSetSemantics {})
            Text(text = label, style = TvTypeScale.body)
        }
    }
}

private const val CHOSEN = "●"
private const val NOT_CHOSEN = "○"

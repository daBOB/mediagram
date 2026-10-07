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
import ui.tv.rememberStableRequester

/**
 * The card menus' radio-choice sections — Speed, Audio, Subtitles and
 * Framing, with the phone's own rows and labels and, as on the web and the
 * phone, no heading: the tool that opened one names it, and a 540 dp screen
 * has no line to spare above six speeds. Each is a
 * plain function of what it shows and what choosing does, as the phone's
 * are, and opens with [current] on the value already chosen — or the first,
 * before one is.
 */
@Composable
internal fun TvSpeedSection(
    speed: Float,
    onChosen: (Float) -> Unit,
    current: FocusRequester,
) {
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
    current: FocusRequester? = null,
) {
    val landing = options.firstOrNull { it.selected } ?: options.firstOrNull()
    for (option in options) {
        TvChoiceRow(label = option.text, selected = option.selected, onClick = { onChosen(option) }, focusRequester = current?.takeIf { option == landing })
    }
}

@Composable
internal fun TvSubtitleSection(
    options: List<SubtitleOption>,
    onChosen: (String) -> Unit,
    current: FocusRequester? = null,
) {
    val landing = options.firstOrNull { it.selected } ?: options.firstOrNull()
    for (option in options) {
        TvChoiceRow(label = option.label, selected = option.selected, onClick = { onChosen(option.value) }, focusRequester = current?.takeIf { option == landing })
    }
}

@Composable
internal fun TvFramingSection(
    framing: Framing,
    onChosen: (Framing) -> Unit,
    current: FocusRequester? = null,
) {
    for (option in Framing.entries) {
        TvChoiceRow(label = option.label, selected = option == framing, onClick = { onChosen(option) }, focusRequester = current?.takeIf { option == framing })
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
                .focusRequester(rememberStableRequester(focusRequester))
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

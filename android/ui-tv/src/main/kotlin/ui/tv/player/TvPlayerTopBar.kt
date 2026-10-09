package ui.tv.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import designsystem.Palette
import designsystem.Spacing
import designsystem.TvTypeScale
import model.MediaSet
import player.PlayerMarksState
import player.technicalLine
import player.titleLine

/** `.together-label`: small, heavier, and set wide. */
private val LabelStyle =
    TvTypeScale.body.copy(fontSize = TvTypeScale.eyebrow, fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.em)

/** Finds the title in a test without depending on what it reads. */
internal const val TvPlayerTitleTag = "tv-player-title"

/**
 * The slim bar along the top: what is playing and what the file is — the
 * shared [titleLine] over [technicalLine], set quieter because it answers a
 * question a viewer only sometimes has — and beside it the marks and
 * Notes, the web's top bar. In watch together [label] names who is in the
 * session beside the title ("Together · TV, Tablet"). The title is only read; the marks and Notes
 * are pressed, one press up from the seek bar, and Down from them goes
 * straight back to it. Beside the episode list there is room for the marks
 * or the title, not both: the marks stay, being pressed, and the list's
 * "Now playing" row names the title meanwhile ([titleShown]).
 */
@Composable
internal fun TvPlayerTopBar(
    set: MediaSet?,
    marks: PlayerMarksState?,
    markActions: TvMarksActions,
    onToggleNotes: (() -> Unit)?,
    focus: TvPlayerFocus,
    modifier: Modifier = Modifier,
    titleShown: Boolean = true,
    label: String? = null,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.medium), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
            set?.takeIf { titleShown }?.let {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = titleLine(it),
                        style = TvTypeScale.title,
                        color = Palette.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).testTag(TvPlayerTitleTag),
                    )
                    // The web's `.together-label`, beside the title, in capitals a screen reader hears as written.
                    // Unlike the web's it goes with the title beside the episode list.
                    label?.let { words ->
                        Text(
                            text = words.uppercase(),
                            style = LabelStyle,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            modifier = Modifier.semantics { contentDescription = words },
                        )
                    }
                }
                // As stored, not shouted: the web prints the container and codecs in the case the index recorded them.
                technicalLine(it).takeIf(String::isNotEmpty)?.let { line ->
                    Text(text = line, style = TvTypeScale.body, color = Palette.Figures, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        TvMarksRail(marks = marks, actions = markActions, first = focus.marks, down = focus.seekBar, lastInBar = onToggleNotes == null)
        onToggleNotes?.let { toggle ->
            TvOverlayButton(
                text = "Notes",
                style = TvTypeScale.body,
                enabled = true,
                onClick = toggle,
                modifier =
                    Modifier
                        .focusProperties {
                            down = focus.seekBar
                            right = FocusRequester.Cancel
                            if (marks == null) left = FocusRequester.Cancel
                        }.focusRequester(focus.notes),
                padding = Spacing.medium,
            )
        }
    }
}

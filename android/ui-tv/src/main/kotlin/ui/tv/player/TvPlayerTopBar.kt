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
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import designsystem.Palette
import designsystem.Spacing
import designsystem.TvTypeScale
import model.MediaSet
import player.PlayerMarksState
import player.technicalLine
import player.titleLine

/** Finds the title in a test without depending on what it reads. */
internal const val TvPlayerTitleTag = "tv-player-title"

/**
 * The slim bar along the top: what is playing and what the file is — the
 * shared [titleLine] over [technicalLine], set quieter because it answers a
 * question a viewer only sometimes has — and beside it the marks and
 * Notes, the web's top bar. The title is only read; the marks and Notes
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
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.medium), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
            set?.takeIf { titleShown }?.let {
                Text(text = titleLine(it), style = TvTypeScale.title, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag(TvPlayerTitleTag))
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

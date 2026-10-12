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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import designsystem.Spacing
import designsystem.tvDp
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
 * Notes, the web's top bar. In watch together [label] names who is in the
 * session beside the title ("Together · TV, Tablet"). The title is only read; the marks and Notes
 * are pressed, one press up from the seek bar, and Down from them goes
 * straight back to it. Beside the episode list there is room for the marks
 * or the title, not both: the marks stay, being pressed, and the list's
 * "Now playing" row names the title meanwhile ([titleShown]).
 *
 * Set as the web's `.hud-top`: the title in Fraunces at `.hud .what`'s size,
 * the marks and Notes as `.ghost` labels in the interface face, [TvBarGap]
 * between them — transparent until focused, as the tablet's are, where the
 * web rims each in a hairline. The file line under the title is the
 * television's own: the web's and the tablet's bars carry the title alone.
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
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(TvBarGap), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
            set?.takeIf { titleShown }?.let {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Two lines where the web's `.what` keeps one (`playback.css:72-77`): a
                    // long series name ("Raumschiff Enterprise: Das nächste Jahrhundert")
                    // otherwise fills the TV's narrower title column and the episode
                    // label after it — the one part that says which episode — is cut.
                    Text(
                        text = titleLine(it),
                        style = TvPlayerType.barTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).testTag(TvPlayerTitleTag),
                    )
                    // The web's `.together-label`, beside the title, in capitals a screen reader hears as written.
                    // Unlike the web's it goes with the title beside the episode list.
                    label?.let { words ->
                        Text(
                            text = words.uppercase(),
                            style = TvPlayerType.togetherLabel,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            modifier = Modifier.semantics { contentDescription = words },
                        )
                    }
                }
                // As stored, not shouted: the web prints the container and codecs in the case the index recorded them.
                technicalLine(it).takeIf(String::isNotEmpty)?.let { line ->
                    Text(text = line, style = TvPlayerType.figures, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        TvMarksRail(marks = marks, actions = markActions, first = focus.marks, down = focus.seekBar, lastInBar = onToggleNotes == null)
        onToggleNotes?.let { toggle ->
            TvCardButton(
                text = "Notes",
                style = TvPlayerType.ghost,
                enabled = true,
                onClick = toggle,
                modifier =
                    Modifier
                        .focusProperties {
                            down = focus.seekBar
                            right = FocusRequester.Cancel
                            if (marks == null) left = FocusRequester.Cancel
                        }.focusRequester(focus.notes),
                padding = TvGhostPadding,
            )
        }
    }
}

/** `.hud-top`'s gap, 1.2rem (`playback.css:48-61`): between the title and the bar's buttons, and between those. */
internal val TvBarGap = 19.2.tvDp

/** `.ghost`'s padding either side, 0.85rem (`playback.css:95`). */
internal val TvGhostPadding = 13.6.tvDp

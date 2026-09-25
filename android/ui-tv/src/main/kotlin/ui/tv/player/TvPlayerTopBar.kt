package ui.tv.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import catalog.episodeLabel
import designsystem.Palette
import designsystem.Spacing
import designsystem.TvTypeScale
import model.MediaSet
import player.technicalLine

/**
 * What is playing, and what the file is: the web player's top rail — the
 * title line over [technicalLine], set quieter than the title because it
 * answers a question a viewer only sometimes has. The web keeps both in
 * the player because this is where a viewer is when they want to know
 * what a film actually is.
 *
 * Nothing here is focusable: there is nothing to press, and a stop the
 * remote could land on would be one more press between the viewer and
 * the seek bar.
 */
@Composable
internal fun TvPlayerTopBar(
    set: MediaSet,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
        Text(text = playerTitleLine(set), style = TvTypeScale.title, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        // As stored, not shouted: the web prints the container and codecs in
        // the case the index recorded them.
        technicalLine(set).takeIf(String::isNotEmpty)?.let {
            Text(text = it, style = TvTypeScale.body, color = Palette.Figures, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * How a title is named while it plays — `A Show · S1E4 · Pilot`, or a
 * film's own title alone. The web player's `titleLine`: the show, the
 * episode, the title, each left out when there is none.
 */
internal fun playerTitleLine(set: MediaSet): String =
    listOf(set.show.orEmpty(), episodeLabel(set), set.title)
        .filter(String::isNotEmpty)
        .joinToString(" · ")

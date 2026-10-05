package ui.tv.player

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import designsystem.Overscan
import designsystem.Palette
import designsystem.Spacing
import designsystem.TvTypeScale
import player.EpisodeList
import ui.player.playerCard

/** Finds the episode list in a test. */
internal const val TvEpisodeSidebarTag = "tv-episode-sidebar"

/** The web's 360px column: the film keeps most of the screen while a viewer picks. */
internal val TvSidebarWidth = 360.dp

/**
 * The run's episodes — or a course's lessons — down the right of the
 * picture, which ☰ opens and the film keeps playing behind: a season at a
 * time under `‹ Season N ›`, opening on the one playing, with the remote on
 * the row that reads "Now playing".
 *
 * The remote cannot wander out of it, as it could not out of the settings
 * panel that stood here before: Left from a row would otherwise land on the
 * controls behind it, where a press skips the film. ✕ and Back close it;
 * picking a row plays it ([onPick]).
 *
 * Not lazy: every row of the season is composed, so the playing one can take
 * the remote the moment the list opens — a lazy row off-screen has nothing
 * attached to take it. A season of hundreds would want a lazy list scrolled
 * to that row first.
 */
@Composable
internal fun TvEpisodeSidebar(
    list: EpisodeList,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var shown by remember(list.currentSection) { mutableIntStateOf(list.currentSection) }
    val section = list.sections[shown.coerceIn(list.sections.indices)]
    // One per row, kept for the list's lifetime: a row's modifier chain never changes shape.
    val requesters = remember { HashMap<String, FocusRequester>() }

    fun requesterOf(setId: String) = requesters.getOrPut(setId) { FocusRequester() }
    val close = remember { FocusRequester() }
    val playing = list.sections.getOrNull(list.currentSection)?.rows?.firstOrNull { it.current }?.setId
    LaunchedEffect(Unit) { (playing?.let(::requesterOf) ?: close).requestFocus() }

    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .width(TvSidebarWidth)
                .playerCard()
                .testTag(TvEpisodeSidebarTag)
                .focusProperties { onExit = { cancelFocusChange() } }
                .focusGroup()
                // Inside the overscan margin on the edge it meets; the side facing the picture needs only breathing room.
                .padding(start = Spacing.medium, end = Overscan.horizontal, top = Overscan.vertical, bottom = Overscan.vertical),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
            if (list.sections.size > 1) {
                TvGlyphButton(glyph = "‹", description = "Previous season", enabled = shown > 0, onClick = { shown -= 1 }, padding = Spacing.small)
            }
            Text(
                text = section.title,
                style = TvTypeScale.body,
                color = Palette.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (list.sections.size > 1) {
                TvGlyphButton(glyph = "›", description = "Next season", enabled = shown < list.sections.lastIndex, onClick = { shown += 1 }, padding = Spacing.small)
            }
            TvGlyphButton(glyph = "✕", description = "Close episodes", enabled = true, onClick = onClose, modifier = Modifier.focusRequester(close), padding = Spacing.small)
        }
        Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
            for (row in section.rows) {
                key(row.setId) { TvEpisodeRow(row, requesterOf(row.setId), onPick) }
            }
        }
    }
}

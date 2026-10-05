package ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import designsystem.Spacing
import player.EpisodeList

/** Finds the sidebar in a test. */
internal const val EpisodeSidebarTag = "episode-sidebar"

/** Beside the picture on anything wide enough to leave the film in view. */
private val SIDEBAR_WIDTH = 320.dp

/** Under this a 320dp column would leave a sliver of film, so the list takes the window. */
private const val FULL_WIDTH_BELOW_DP = 600

/**
 * The run's titles, a season (or a course's section) at a time, standing
 * down the right of the picture while the film keeps playing — the web's
 * sidebar on the phone. It opens on the section holding the open title
 * ([EpisodeList.currentSection]); ‹ › step through the others and with one
 * section there is nothing to step to, so only its title shows. Kept on the
 * section a viewer stepped to while rows change under it — a title finished
 * elsewhere re-greys its row without moving the list.
 */
@Composable
internal fun EpisodeSidebar(
    list: EpisodeList,
    onPick: (setId: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var shown by remember(list.currentSection) { mutableIntStateOf(list.currentSection) }
    val section = list.sections.getOrNull(shown) ?: list.sections.firstOrNull() ?: return
    val narrow = LocalConfiguration.current.screenWidthDp < FULL_WIDTH_BELOW_DP

    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Column(
            modifier =
                modifier
                    .fillMaxHeight()
                    .then(if (narrow) Modifier.fillMaxWidth() else Modifier.width(SIDEBAR_WIDTH))
                    .testTag(EpisodeSidebarTag)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .playerCard(),
        ) {
            SectionHeader(
                title = section.title,
                arrows = list.sections.size > 1,
                onPrevious = if (shown > 0) { { shown -= 1 } } else null,
                onNext = if (shown < list.sections.lastIndex) { { shown += 1 } } else null,
                onClose = onClose,
            )
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(section.rows, key = { it.setId }) { row -> EpisodeSidebarRow(row = row, onPick = onPick) }
            }
        }
    }
}

/** `‹ Season N ›` and ✕. An arrow with nowhere to go is disabled rather than gone, so the title does not jump. */
@Composable
private fun SectionHeader(
    title: String,
    arrows: Boolean,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onClose: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(Spacing.small), verticalAlignment = Alignment.CenterVertically) {
        if (arrows) GlyphButton(glyph = "‹", description = "Previous season", enabled = onPrevious != null, onClick = { onPrevious?.invoke() })
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = Spacing.small),
        )
        if (arrows) GlyphButton(glyph = "›", description = "Next season", enabled = onNext != null, onClick = { onNext?.invoke() })
        GlyphButton(glyph = "✕", description = "Close episodes", enabled = true, onClick = onClose)
    }
}

package ui.catalog.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import catalog.factsLine
import model.MediaSet
import ui.catalog.PosterCard
import ui.common.catalog.rememberRowState

/**
 * Latest documentaries: one scrolling row of titles, each played from its
 * plate — the web's `movieGrid(latestDocumentaries, play, {mode: GRID, strip: true})`
 * (`home-view.js`). Sized as [HomeShelfRow]'s plates are, so the rows above
 * and below it line up.
 */
@Composable
internal fun HomeTitleRow(
    sets: List<MediaSet>,
    width: Dp,
    watchedIds: Set<String>,
    onPlay: (setId: String) -> Unit,
) {
    if (sets.isEmpty()) return
    // `max(136dp, (column - 98dp) / 8)`, the strip's own auto-column formula, as [HomeShelfRow] reads it.
    val cardWidth = ((width - 98.dp) / 8f).coerceAtLeast(136.dp)
    LazyRow(state = rememberRowState(sets.map(MediaSet::setId)), horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 4.dp)) {
        items(items = sets, key = MediaSet::setId) { set ->
            PosterCard(
                posterPath = set.posterPath,
                title = set.title,
                caption = factsLine(set.year, set.durationSecs),
                watched = set.setId in watchedIds,
                modifier = Modifier.width(cardWidth),
                onClick = { onPlay(set.setId) },
            )
        }
    }
}

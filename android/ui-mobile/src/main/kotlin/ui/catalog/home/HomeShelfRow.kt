package ui.catalog.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import catalog.Entry
import catalog.initialsOf
import coil3.compose.AsyncImage
import java.io.File
import ui.catalog.rememberRowState

/**
 * Latest series: one scrolling row of poster cards, each captioned with its
 * name and how much of it this library holds — a Compose port of
 * `collectionGrid("series", ..., {mode: GRID, strip: true})`
 * (`home-view.js:103-106`). Up to eight, the same limit the web's own
 * poster rows hold (`home-shelves.js`'s `POSTER_ROW_LIMIT`).
 */
@Composable
internal fun HomeShelfRow(
    shows: List<Entry.Collection>,
    width: Dp,
    compact: Boolean,
    onOpen: (String) -> Unit,
) {
    if (shows.isEmpty()) return
    // `max(136dp, (column - 98dp) / 8)` (`catalog.css:35`, the strip's own auto-column formula, at the row's own width).
    val cardWidth = ((width - 98.dp) / 8f).coerceAtLeast(136.dp)
    LazyRow(state = rememberRowState(shows.map(Entry.Collection::key)), horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 4.dp)) {
        items(items = shows, key = Entry.Collection::key) { show ->
            Column(modifier = Modifier.width(cardWidth).clickable(role = Role.Button, onClick = { onOpen(show.key) })) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    val poster = show.posterPath
                    if (poster != null) {
                        AsyncImage(model = File(poster), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                    } else {
                        Text(
                            text = initialsOf(show.name),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
                Text(
                    text = show.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = if (compact) 14.sp else 17.sp, lineHeight = 1.25.em),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = "${countOf(show.count, "episode")} · ${countOf(show.chapters, "season")}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

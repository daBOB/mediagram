package ui.catalog.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import coil3.compose.AsyncImage
import designsystem.Eyebrow
import model.MediaSet
import ui.common.catalog.rememberRowState
import java.io.File

/**
 * Recently Added — posters alone, no caption, the poster standing for the
 * whole card — beside This month, the same 2.6fr/1fr split [ContinueBand]
 * uses. A Compose port of the `.home-band.home-library` block
 * (`home-view.js:90-101`).
 */
@Composable
internal fun RecentBand(
    recentlyAdded: List<MediaSet>,
    totalFilms: Int,
    thisMonth: List<MediaSet>,
    width: Dp,
    onOpenTitle: (String) -> Unit,
    onSeeAllMovies: () -> Unit,
) {
    if (recentlyAdded.isEmpty() && thisMonth.isEmpty()) return
    val gutter = gutterFor(width)
    val compact = width <= CompactBreakpoint
    val recentBlock: @Composable () -> Unit = {
        if (recentlyAdded.isNotEmpty()) {
            Column {
                BandHeading(title = "Recently Added", count = totalFilms, onSeeAll = onSeeAllMovies)
                PosterStrip(films = recentlyAdded, onOpenTitle = onOpenTitle, modifier = Modifier.padding(top = 20.dp))
            }
        }
    }
    val monthBlock: @Composable () -> Unit = { if (thisMonth.isNotEmpty()) ThisMonth(thisMonth, onOpenTitle) }

    if (compact) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = gutter), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            recentBlock()
            monthBlock()
        }
        return
    }
    when {
        recentlyAdded.isEmpty() -> Box(Modifier.fillMaxWidth().padding(horizontal = gutter)) { monthBlock() }
        thisMonth.isEmpty() -> Box(Modifier.fillMaxWidth().padding(horizontal = gutter)) { recentBlock() }
        else ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = gutter),
                horizontalArrangement = Arrangement.spacedBy(fluid(32f, 0.04f, 72f, width.value).dp),
            ) {
                Box(Modifier.weight(2.6f)) { recentBlock() }
                Box(Modifier.weight(1f)) { monthBlock() }
            }
    }
}

/** A poster row with nothing under it — the poster is the whole card, `{captions: false}` (`home-view.js:94`). */
@Composable
private fun PosterStrip(
    films: List<MediaSet>,
    onOpenTitle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(state = rememberRowState(films.map(MediaSet::setId)), modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 4.dp)) {
        items(items = films, key = MediaSet::setId) { set ->
            Box(
                modifier =
                    Modifier
                        .width(136.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(role = Role.Button, onClick = { onOpenTitle(set.setId) }),
            ) {
                val poster = set.posterPath
                if (poster != null) {
                    AsyncImage(model = File(poster), contentDescription = set.title, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                } else {
                    Text(
                        text = set.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.Center).padding(8.dp),
                    )
                }
            }
        }
    }
}

/**
 * The contents page, numbered, in a ruled column — `.this-month`
 * (`home.css:306-324`): one rule down the column's own left edge, and one
 * under each row, rather than a border around every row (which drew a box
 * on all four sides of each, including doubled-up rules between rows).
 */
@Composable
private fun ThisMonth(
    films: List<MediaSet>,
    onOpenTitle: (String) -> Unit,
) {
    Row(modifier = Modifier.height(IntrinsicSize.Max)) {
        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(text = "This month".uppercase(), style = Eyebrow, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(bottom = 8.dp))
            for ((index, set) in films.withIndex()) {
                Column {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button, onClick = { onOpenTitle(set.setId) })
                                .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = (index + 1).toString().padStart(2, '0'),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal, fontSize = 20.8.sp, lineHeight = 1.em),
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.width(35.dp),
                        )
                        Column {
                            Text(
                                text = set.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 1.3.em),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val meta = listOfNotNull(set.year?.takeIf { it > 0 }?.toString(), set.genres.firstOrNull()).joinToString(" · ")
                            if (meta.isNotEmpty()) {
                                Text(
                                    text = meta,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

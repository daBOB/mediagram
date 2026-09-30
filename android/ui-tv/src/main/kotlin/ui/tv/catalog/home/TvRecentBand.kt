package ui.tv.catalog.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import designsystem.Eyebrow
import designsystem.Spacing
import designsystem.TvTypeScale
import java.io.File
import model.MediaSet
import ui.tv.TvFocus
import ui.tv.TvTextRow

private val RecentPosterWidth = 160.dp

/**
 * Recently added — posters alone, no caption — beside This month, the same
 * 2.6fr/1fr split [TvContinueBand] uses. The television twin of the phone's
 * `RecentBand`.
 */
@Composable
internal fun TvRecentBand(
    recentlyAdded: List<MediaSet>,
    totalFilms: Int,
    thisMonth: List<MediaSet>,
    onOpenTitle: (String) -> Unit,
    onSeeAllMovies: () -> Unit,
    // Where the requester ends up (which poster, at [focusAt]'s own index)
    // — whether and when it is actually asked to take focus is `TvHome`'s
    // own call, made once after its outer list has confirmed this whole
    // band is really composed, not this band's to decide on its own mount.
    focusAt: Int? = null,
    focus: FocusRequester? = null,
) {
    if (recentlyAdded.isEmpty() && thisMonth.isEmpty()) return
    val link = remember { SeeAllLink() }
    val recentBlock: @Composable () -> Unit = {
        if (recentlyAdded.isNotEmpty()) {
            Column {
                TvBandHeading(title = "Recently Added", count = totalFilms) {
                    TvTextRow(text = "See all", onClick = onSeeAllMovies, modifier = link.seeAll, focusRequester = link.focus)
                }
                TvRecentPosterRow(recentlyAdded, onOpenTitle, focusAt, focus, link.lastStop)
            }
        }
    }
    val monthBlock: @Composable () -> Unit = { if (thisMonth.isNotEmpty()) TvThisMonth(thisMonth, onOpenTitle) }

    when {
        recentlyAdded.isEmpty() -> Box(Modifier.fillMaxWidth()) { monthBlock() }
        thisMonth.isEmpty() -> Box(Modifier.fillMaxWidth()) { recentBlock() }
        else ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.extraLarge)) {
                Box(Modifier.weight(2.6f)) { recentBlock() }
                Box(Modifier.weight(1f)) { monthBlock() }
            }
    }
}

/**
 * A poster row with nothing under it — the poster is the whole card, the
 * phone's own `{captions: false}`. A plain row, always fully composed (at
 * most [catalog.HOME_POSTER_ROW_LIMIT] posters, not a plate wall's own
 * hundreds): arrival only ever has to focus the right one, and Compose's
 * own scrollable-ancestor relocation brings it into view.
 */
@Composable
private fun TvRecentPosterRow(
    films: List<MediaSet>,
    onOpenTitle: (String) -> Unit,
    focusAt: Int?,
    focus: FocusRequester?,
    lastStop: Modifier,
) {
    Row(
        modifier = Modifier.padding(top = Spacing.medium).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        films.forEachIndexed { index, set ->
            key(set.setId) {
                // Never omitted — see the same doc on `TvResumeCard`'s own `ownRequester`.
                val ownRequester = remember { FocusRequester() }
                Card(
                    onClick = { onOpenTitle(set.setId) },
                    modifier =
                        Modifier
                            .keepsInViewWhenMoved(index)
                            .width(RecentPosterWidth)
                            .semantics(mergeDescendants = true) {}
                            .focusRequester(if (index == focusAt) focus ?: ownRequester else ownRequester)
                            .let { if (index == films.lastIndex) it.then(lastStop) else it },
                    shape = TvFocus.cardShape(),
                    scale = TvFocus.cardScale(),
                    border = TvFocus.cardBorder(),
                    glow = TvFocus.cardGlow(),
                    colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                        val poster = set.posterPath
                        if (poster != null) {
                            AsyncImage(model = File(poster), contentDescription = set.title, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                        } else {
                            Text(
                                text = set.title,
                                style = TvTypeScale.body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.align(Alignment.Center).padding(Spacing.small),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The contents page, numbered, in a ruled column — the television twin of
 * the phone's `ThisMonth`: one rule down the column's own left edge, and
 * one under each row.
 */
@Composable
private fun TvThisMonth(
    films: List<MediaSet>,
    onOpenTitle: (String) -> Unit,
) {
    Row(modifier = Modifier.height(IntrinsicSize.Max)) {
        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.border))
        Column(modifier = Modifier.padding(start = Spacing.medium)) {
            Text(
                text = "This month".uppercase(),
                style = Eyebrow.copy(fontSize = TvTypeScale.eyebrow),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.small),
            )
            for ((index, set) in films.withIndex()) {
                Column {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button, onClick = { onOpenTitle(set.setId) })
                                .padding(vertical = Spacing.small),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                    ) {
                        Text(
                            text = (index + 1).toString().padStart(2, '0'),
                            style = TvTypeScale.title.copy(fontSize = TvTypeScale.body.fontSize),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(35.dp),
                        )
                        Column {
                            Text(text = set.title, style = TvTypeScale.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val meta = listOfNotNull(set.year?.takeIf { it > 0 }?.toString(), set.genres.firstOrNull()).joinToString(" · ")
                            if (meta.isNotEmpty()) {
                                Text(
                                    text = meta,
                                    style = TvTypeScale.body.copy(fontSize = TvTypeScale.eyebrow),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(MaterialTheme.colorScheme.border))
                }
            }
        }
    }
}

package ui.tv.catalog.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import designsystem.Spacing
import designsystem.TvType
import designsystem.tvSp
import java.io.File
import model.MediaSet
import ui.tv.TvFocus
import ui.tv.TvTextRow
import ui.tv.catalog.HomeBandGap
import ui.tv.catalog.RowHeadBelow
import ui.tv.catalog.StripGap
import ui.tv.catalog.rowArtOf
import ui.tv.catalog.rowArtRatio
import ui.tv.catalog.rowCardsAreLandscape
import ui.tv.catalog.rowPlateWidth
import ui.tv.rememberStableRequester

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
                    TvTextRow(text = "See all", onClick = onSeeAllMovies, modifier = link.seeAll, focusRequester = link.focus, style = TvType.bodySmall)
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HomeBandGap)) {
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
 * own scrollable-ancestor relocation brings it into view. Its posters are
 * [stripPlateWidth] of the band's own column, as the web's strip measures
 * its columns against the band column it sits in (`home-view.js:94`).
 */
@Composable
private fun TvRecentPosterRow(
    films: List<MediaSet>,
    onOpenTitle: (String) -> Unit,
    focusAt: Int?,
    focus: FocusRequester?,
    lastStop: Modifier,
) {
    BoxWithConstraints {
        val landscape = rowCardsAreLandscape
        val plateWidth = rowPlateWidth(maxWidth)
        Row(
            modifier = Modifier.padding(top = RowHeadBelow).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(StripGap),
        ) {
            films.forEachIndexed { index, set ->
                key(set.setId) {
                    Card(
                        onClick = { onOpenTitle(set.setId) },
                        modifier =
                            Modifier
                                .keepsInViewWhenMoved(index)
                                .width(plateWidth)
                                .semantics(mergeDescendants = true) {}
                                .focusRequester(rememberStableRequester(focus?.takeIf { index == focusAt }))
                                .let { if (index == films.lastIndex) it.then(lastStop) else it },
                        shape = TvFocus.cardShape(),
                        scale = TvFocus.cardScale(),
                        border = TvFocus.cardBorder(),
                        glow = TvFocus.cardGlow(),
                        colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().aspectRatio(rowArtRatio)) {
                            val art = rowArtOf(landscape, set.posterPath, set.backdropPath)
                            if (art != null) {
                                AsyncImage(model = File(art), contentDescription = set.title, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                            } else {
                                Text(
                                    text = set.title,
                                    style = TvType.titleSmall.copy(fontSize = 15.tvSp, lineHeight = 1.3.em),
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
}

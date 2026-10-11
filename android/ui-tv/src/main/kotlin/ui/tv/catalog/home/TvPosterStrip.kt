package ui.tv.catalog.home

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.Entry
import catalog.initialsOf
import catalog.spelledCountOf
import coil3.compose.AsyncImage
import designsystem.Spacing
import designsystem.TvType
import designsystem.tvSp
import java.io.File
import ui.tv.TvFocus
import ui.tv.catalog.StripGap
import ui.tv.catalog.rowArtOf
import ui.tv.catalog.rowArtRatio
import ui.tv.catalog.rowCardsAreLandscape
import ui.tv.catalog.rowPlateWidth
import ui.tv.rememberStableRequester

/**
 * Latest series: one scrolling row of poster cards, each captioned with its
 * name and how much of it this library holds — "21 episodes · three
 * seasons" — the television twin of the phone's `HomeShelfRow`, cut to
 * [ui.tv.TvPlate]'s own poster shape — or, in a style whose rows are stills, to a still ([rowCardsAreLandscape]) — with a Fraunces caption, each poster
 * [stripPlateWidth] of the row as the web's strip sets it. A plain row,
 * always fully composed (at most
 * [catalog.HOME_POSTER_ROW_LIMIT] posters): Compose's own scrollable-
 * ancestor relocation brings a newly focused one into view on its own.
 */
@Composable
internal fun TvPosterStrip(
    shows: List<Entry.Collection>,
    onOpen: (String) -> Unit,
    // Where the requester ends up (which poster, at [focusAt]'s own index)
    // — whether and when it is actually asked to take focus is `TvHome`'s
    // own call, made once after its outer list has confirmed this whole
    // strip is really composed, not this strip's to decide on its own mount.
    focusAt: Int? = null,
    focus: FocusRequester? = null,
    lastStop: Modifier = Modifier,
) {
    if (shows.isEmpty()) return
    BoxWithConstraints {
        val landscape = rowCardsAreLandscape
        val plateWidth = rowPlateWidth(maxWidth)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(StripGap),
        ) {
            shows.forEachIndexed { index, show ->
                key(show.key) {
                    // Poster, name and caption all inside the one `Card` — the same
                    // shape [ui.tv.TvPlate] draws — rather than a caption sitting
                    // outside it as a layout sibling: `mergeDescendants` below only
                    // ever folds a focusable node's own descendants into it, never
                    // a sibling's, so a caption a viewer reads as part of "this
                    // plate" has to actually be one of its children to read as
                    // focused along with it.
                    Card(
                        onClick = { onOpen(show.key) },
                        modifier =
                            Modifier
                                .keepsInViewWhenMoved(index)
                                .width(plateWidth)
                                .semantics(mergeDescendants = true) {}
                                .focusRequester(rememberStableRequester(focus?.takeIf { index == focusAt }))
                                .let { if (index == shows.lastIndex) it.then(lastStop) else it },
                        shape = TvFocus.cardShape(),
                        scale = TvFocus.cardScale(),
                        border = TvFocus.cardBorder(),
                        glow = TvFocus.cardGlow(),
                        colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column {
                            Box(modifier = Modifier.fillMaxWidth().aspectRatio(rowArtRatio)) {
                                val art = rowArtOf(landscape, show.posterPath, show.backdropPath)
                                if (art != null) {
                                    AsyncImage(model = File(art), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                                } else {
                                    Text(
                                        text = initialsOf(show.name),
                                        style = TvType.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.Center),
                                    )
                                }
                            }
                            Text(
                                text = show.name,
                                style = TvType.titleSmall.copy(fontSize = 15.tvSp, lineHeight = 1.25.em),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = Spacing.small, start = Spacing.small, end = Spacing.small),
                            )
                            Text(
                                text = "${spelledCountOf(show.count, "episode")} · ${spelledCountOf(show.chapters, "season")}",
                                style = TvType.labelSmall.copy(fontSize = 12.tvSp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp, start = Spacing.small, end = Spacing.small, bottom = Spacing.small),
                            )
                        }
                    }
                }
            }
        }
    }
}

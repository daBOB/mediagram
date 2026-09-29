package ui.tv.catalog.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.Entry
import catalog.initialsOf
import catalog.spelledCountOf
import coil3.compose.AsyncImage
import designsystem.CoverTitle
import designsystem.Spacing
import designsystem.TvTypeScale
import java.io.File
import ui.tv.TvFocus

private val PosterWidth = 160.dp

/**
 * Latest series: one scrolling row of poster cards, each captioned with its
 * name and how much of it this library holds — "21 episodes · three
 * seasons" — the television twin of the phone's `HomeShelfRow`, cut to
 * [ui.tv.TvPlate]'s own poster shape with a Fraunces caption rather than
 * that row's `%`-of-column formula: TV has one fixed width, not a rail that
 * narrows it. A plain row, always fully composed (at most
 * [catalog.HOME_POSTER_ROW_LIMIT] posters): Compose's own scrollable-
 * ancestor relocation brings a newly focused one into view on its own.
 */
@Composable
internal fun TvPosterStrip(
    shows: List<Entry.Collection>,
    onOpen: (String) -> Unit,
    focusAt: Int? = null,
    focus: FocusRequester? = null,
    lastStop: Modifier = Modifier,
    // Read fresh inside the effect below, never added to its own key — see
    // [TvCourseList]'s own doc on the same parameter for why.
    takesFocus: Boolean = true,
) {
    if (shows.isEmpty()) return
    LaunchedEffect(focusAt) {
        if (focusAt == null || focus == null || !takesFocus) return@LaunchedEffect
        focus.requestFocus()
    }
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        shows.forEachIndexed { index, show ->
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
                        .width(PosterWidth)
                        .semantics(mergeDescendants = true) {}
                        .let { if (index == focusAt && focus != null) it.focusRequester(focus) else it }
                        .let { if (index == shows.lastIndex) it.then(lastStop) else it },
                shape = TvFocus.cardShape(),
                scale = TvFocus.cardScale(),
                border = TvFocus.cardBorder(),
                glow = TvFocus.cardGlow(),
                colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column {
                    Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                        val poster = show.posterPath
                        if (poster != null) {
                            AsyncImage(model = File(poster), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                        } else {
                            Text(
                                text = initialsOf(show.name),
                                style = TvTypeScale.title,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                    }
                    Text(
                        text = show.name,
                        style = CoverTitle.copy(fontSize = 18.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = Spacing.small, start = Spacing.small, end = Spacing.small),
                    )
                    Text(
                        text = "${spelledCountOf(show.count, "episode")} · ${spelledCountOf(show.chapters, "season")}",
                        style = TvTypeScale.body.copy(fontSize = TvTypeScale.eyebrow),
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

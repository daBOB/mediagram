package ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import catalog.Entry
import catalog.KeptKind
import catalog.KidsShelf
import catalog.SetCard
import catalog.resumeLine
import designsystem.Spacing
import model.MediaSet
import model.WatchSnapshot

/**
 * One of the three kept walls that hold titles directly — Continue,
 * Watchlist, Kids — drawn with [SetPlate], the same plate Continue and Next
 * up already use on the start page. Collections is not one of these: its
 * shelf is a list of lists, not of titles, and lives in [ListsScreen].
 *
 * The caption is [catalog.resumeLine] on every one of the three, matching
 * `setGrid`'s own default in `shelf-view.js` — most Watchlist and Kids
 * plates have no position to report and simply say nothing under the name.
 */
@Composable
internal fun KeptWall(
    kind: KeptKind,
    sets: List<MediaSet>,
    watch: WatchSnapshot,
    columns: Int,
    onOpenTitle: (setId: String) -> Unit,
    heldIds: Set<String> = emptySet(),
) {
    Column(modifier = Modifier.fillMaxSize()) {
        WallHeading(kind.label, sets.size)
        if (sets.isEmpty()) {
            CenteredMessage(kind.empty)
            return
        }
        val positions = watch.progress.associateBy { it.setId }
        val watchedIds = watch.watched.mapTo(HashSet()) { it.setId }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            items(items = sets, key = MediaSet::setId) { set ->
                SetPlate(
                    card = SetCard(
                        set = set,
                        caption = resumeLine(positions[set.setId]),
                        progress = watchedFractionOf(positions[set.setId]),
                        watched = set.setId in watchedIds,
                        held = set.setId in heldIds,
                    ),
                    onClick = { onOpenTitle(set.setId) },
                )
            }
        }
    }
}

/**
 * "Title · n", the same heading the start page's own rows use — asked for
 * explicitly in the phase's requirements rather than the web's spelled-out
 * `countOf` wording (`heading()` in app.js), so a viewer reading both
 * Continue's row on the start page and its own tab sees one convention, not
 * two.
 */
@Composable
private fun WallHeading(title: String, total: Int) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.medium, vertical = Spacing.small)) {
        Text(
            text = buildAnnotatedString {
                append(title)
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    append(" · $total")
                }
            },
            style = MaterialTheme.typography.titleLarge,
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = Spacing.small),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

/**
 * The Kids tab — `viewKids` in app.js: what the ratings put there, then what
 * was marked by hand, each under its own heading once there is more than one
 * kind to tell apart. A show's card opens the show, as it does on its own
 * shelf; a hand-marked title plays directly into the marked-by-hand run —
 * `app.js:552`'s `setGrid(byHand, (set) => play(set, byHand))` — rather than
 * opening its own show or standing alone; no Play all here, unlike a list's:
 * the web has none on this wall either.
 */
@Composable
internal fun KidsWall(
    shelf: KidsShelf,
    watch: WatchSnapshot,
    columns: Int,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onPlayRun: (setId: String, run: List<String>) -> Unit,
    heldIds: Set<String> = emptySet(),
) {
    Column(modifier = Modifier.fillMaxSize()) {
        WallHeading(KeptKind.KIDS.label, shelf.total)
        if (shelf.total == 0) {
            CenteredMessage(KeptKind.KIDS.empty)
            return
        }
        val positions = watch.progress.associateBy { it.setId }
        val watchedIds = watch.watched.mapTo(HashSet()) { it.setId }
        val byHandIds = shelf.byHand.map(MediaSet::setId)
        val parts = listOf("Movies" to shelf.films, "Series" to shelf.series, "Marked by hand" to shelf.byHand)
            .filter { (_, items) -> items.isNotEmpty() }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            for ((label, items) in parts) {
                if (parts.size > 1) {
                    item(key = "heading-$label", span = { GridItemSpan(maxLineSpan) }) {
                        Text(text = label, style = MaterialTheme.typography.titleMedium)
                    }
                }
                items(items = items, key = { it.keyOfKids() }) { item ->
                    when (item) {
                        is Entry -> EntryCard(item, positions, watchedIds, onOpenTitle, onOpenCollection)
                        is MediaSet -> SetPlate(
                            card = SetCard(
                                set = item,
                                caption = resumeLine(positions[item.setId]),
                                progress = watchedFractionOf(positions[item.setId]),
                                watched = item.setId in watchedIds,
                                held = item.setId in heldIds,
                            ),
                            onClick = { onPlayRun(item.setId, byHandIds) },
                        )
                    }
                }
            }
        }
    }
}

/** A set may be on the shelf as a film and never again as a hand-marked plate, so ids do not collide. */
private fun Any.keyOfKids(): String = when (this) {
    is Entry -> keyOf(this)
    is MediaSet -> "hand-$setId"
    else -> error("not a Kids item: $this")
}

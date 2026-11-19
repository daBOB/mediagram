package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import catalog.KeptKind
import catalog.SetCard
import catalog.cardPosition
import catalog.factsLine
import catalog.heldOf
import catalog.playedOf
import catalog.resumeLine
import catalog.versionsBadgeOf
import catalog.watchedFractionOf
import data.settings.ShelfView
import designsystem.Spacing
import model.MediaSet
import model.WatchSnapshot
import model.episodeLabel
import ui.chrome.CatalogueTextButton

/**
 * One of the two kept walls that hold titles directly — Continue and
 * Watchlist — drawn with [SetPlate], the same plate Continue and Next
 * up already use on the start page, or as rows when this device's shelf
 * view is the list: the web's `keptWall` in `app.js` offers Continue and My
 * List the Movies heading's List · Grid, and so does this, through the same
 * [ShelfViewChoice] the Movies shelf reads. Collections is not one of these:
 * its shelf is a list of lists, not of titles, and lives in [ListsScreen].
 *
 * The caption is [catalog.resumeLine] on both, matching `setGrid`'s own
 * default in `shelf-view.js` — most Watchlist plates have no position to report and simply say nothing under the name.
 */
@Composable
internal fun KeptWall(
    kind: KeptKind,
    sets: List<MediaSet>,
    watch: WatchSnapshot,
    columns: Int,
    view: ShelfViewChoice,
    onOpenTitle: (setId: String) -> Unit,
    heldIds: Set<String> = emptySet(),
    onFinish: ((setId: String) -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        WallHeading(kind.label, sets.size, view.takeIf { sets.isNotEmpty() })
        if (sets.isEmpty()) {
            CenteredMessage(kind.empty)
            return
        }
        val positions = watch.progress.associateBy { it.setId }
        val watchedIds = watch.watched.mapTo(HashSet()) { it.setId }
        val watchedAt = watch.watched.associate { it.setId to it.finishedAt }
        val cards = sets.map { set ->
            // A film held more than once reads as the film: played by any copy, at its newest place.
            val position = cardPosition(set, positions, watchedAt::get)
            SetCard(set, resumeLine(position), watchedFractionOf(position), playedOf(set, watchedIds), heldOf(set, heldIds))
        }
        if (view.chosen == ShelfView.LIST) {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Spacing.medium)) {
                items(items = cards, key = { it.set.setId }) { card ->
                    val set = card.set
                    Column {
                        ShelfRow(
                            title = set.title,
                            caption = listOfNotNull(set.show, episodeLabel(set).ifEmpty { null }, factsLine(set.year, set.durationSecs))
                                .joinToString(" · ").ifEmpty { null },
                            resume = card.caption.ifEmpty { null },
                            watched = card.watched,
                            held = card.held,
                            progress = card.progress,
                            versions = versionsBadgeOf(set),
                            onClick = { onOpenTitle(set.setId) },
                        )
                        MarkFinished(onFinish?.let { { it(set.setId) } })
                        HorizontalDivider()
                    }
                }
            }
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            items(items = cards, key = { it.set.setId }) { card ->
                Column {
                    SetPlate(card = card, onClick = { onOpenTitle(card.set.setId) })
                    MarkFinished(onFinish?.let { { it(card.set.setId) } })
                }
            }
        }
    }
}

/**
 * Continue's "Mark finished", under the plate or the row rather than on it —
 * `withAction` in the web's `shelf-view.js` keeps it beside the card for the
 * same reason: a control of its own, not one tap away from starting the film
 * by mistake. Nothing when [onFinish] is null, as on My List.
 */
@Composable
private fun ColumnScope.MarkFinished(onFinish: (() -> Unit)?) {
    if (onFinish == null) return
    CatalogueTextButton(onClick = onFinish, modifier = Modifier.align(Alignment.End)) {
        Text("Mark finished")
    }
}

/**
 * "Title · n", the same heading the start page's own rows use, rather than
 * the web's spelled-out `countOf` wording (`heading()` in app.js), so a viewer reading both
 * Continue's row on the start page and its own tab sees one convention, not
 * two.
 */
@Composable
private fun WallHeading(title: String, total: Int, view: ShelfViewChoice?) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.medium, vertical = Spacing.small)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = buildAnnotatedString {
                    append(title)
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        append(" · $total")
                    }
                },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            // No choice over an empty wall, as on the web: there is nothing to lay out.
            view?.let { ShelfModeToggle(it.chosen, it.onChoose) }
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = Spacing.small),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}


package ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import catalog.Entry
import catalog.HomeRow
import catalog.RowContent
import designsystem.Spacing
import model.WatchSnapshot

/**
 * What was already underway, and what arrived recently.
 *
 * The page a viewer lands on. Continue and Next up lead it when there is
 * anything on them — the viewer's own place in the library, ahead of what
 * merely turned up — and both are absent rather than empty, same as the
 * Latest rows below them: a row that is always empty is worse than a row
 * that is not there at all.
 */
@Composable
internal fun HomeScreen(
    rows: List<HomeRow>,
    watch: WatchSnapshot,
    columns: Int,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onSeeAll: (shelf: String) -> Unit,
) {
    val positions = remember(watch) { watch.progress.associateBy { it.setId } }
    val watchedIds = remember(watch) { watch.watched.mapTo(HashSet()) { it.setId } }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        for (row in rows) {
            item(key = row.title, span = { GridItemSpan(maxLineSpan) }) {
                RowHeading(row = row, onSeeAll = row.seeAll?.let { target -> { onSeeAll(target) } })
            }
            when (val content = row.content) {
                is RowContent.Entries -> items(items = content.entries, key = { "${row.title}/${keyOf(it)}" }) { entry ->
                    when (entry) {
                        // A collection card carries no mark of its own — the
                        // web's `collectionGrid` never draws one either, a
                        // show or a course is not one title to finish.
                        is Entry.Film -> PosterCard(
                            posterPath = entry.set.posterPath,
                            title = entry.set.title,
                            caption = factsLine(entry.set.year, entry.set.durationSecs),
                            progress = watchedFractionOf(positions[entry.set.setId]),
                            watched = entry.set.setId in watchedIds,
                            modifier = Modifier,
                            onClick = { onOpenTitle(entry.set.setId) },
                        )

                        is Entry.Collection -> PosterCard(
                            posterPath = entry.posterPath,
                            title = entry.name,
                            caption = extentOf(entry),
                            modifier = Modifier,
                            onClick = { onOpenCollection(entry.key) },
                        )
                    }
                }

                is RowContent.Sets -> items(items = content.cards, key = { "${row.title}/${it.set.setId}" }) { card ->
                    SetPlate(card = card, modifier = Modifier, onClick = { onOpenTitle(card.set.setId) })
                }
            }
        }
    }
}

/**
 * A row's name, and the way through to the whole shelf behind it, or none
 * yet — [onSeeAll] is `null` for Continue until the kept-shelves phase gives
 * it a tab of its own.
 *
 * "See all" is the row admitting it is a window. Six plates out of three
 * hundred is a glance, and a viewer who wants the rest should not have to
 * work out that the masthead is where it lives.
 */
@Composable
private fun RowHeading(row: HomeRow, onSeeAll: (() -> Unit)?) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.medium)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            // The same " · n" the web player's row heading carries.
            Text(
                text = buildAnnotatedString {
                    append(row.title)
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        append(" · ${row.total}")
                    }
                },
                style = MaterialTheme.typography.titleMedium,
            )
            if (onSeeAll != null) {
                Text(
                    text = "See all",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(role = Role.Button, onClick = onSeeAll)
                        .padding(start = Spacing.medium, top = Spacing.small, bottom = Spacing.small),
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = Spacing.small),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

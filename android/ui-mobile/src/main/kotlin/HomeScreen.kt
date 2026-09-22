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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import catalog.Entry
import catalog.HomeRow
import designsystem.Spacing

/**
 * What arrived recently, by shelf.
 *
 * The page a viewer lands on, and the one row of questions this app can
 * currently answer about its own library. Continue and Next up belong here
 * too and are not here: the phone keeps no watch state, so there is nothing
 * to read for them, and a row that is always empty is worse than a row that
 * is absent.
 */
@Composable
internal fun HomeScreen(
    rows: List<HomeRow>,
    columns: Int,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onSeeAll: (shelf: String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        for (row in rows) {
            item(key = row.title, span = { GridItemSpan(maxLineSpan) }) {
                RowHeading(row = row, onSeeAll = { onSeeAll(row.shelf) })
            }
            items(items = row.entries, key = { "${row.shelf}/${keyOf(it)}" }) { entry ->
                when (entry) {
                    is Entry.Film -> PosterCard(
                        posterPath = entry.set.posterPath,
                        title = entry.set.title,
                        caption = factsLine(entry.set.year, entry.set.durationSecs),
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
        }
    }
}

/**
 * A row's name, and the way through to the whole shelf behind it.
 *
 * "See all" is the row admitting it is a window. Six plates out of three
 * hundred is a glance, and a viewer who wants the rest should not have to
 * work out that the masthead is where it lives.
 */
@Composable
private fun RowHeading(row: HomeRow, onSeeAll: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.medium)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(text = row.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "See all",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = onSeeAll)
                    .padding(start = Spacing.medium, top = Spacing.small, bottom = Spacing.small),
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = Spacing.small),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

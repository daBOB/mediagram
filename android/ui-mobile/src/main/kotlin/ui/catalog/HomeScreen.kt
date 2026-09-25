package ui.catalog

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
import androidx.compose.foundation.lazy.grid.LazyGridScope
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
import catalog.HomeRow
import catalog.MagazineHome
import catalog.RowContent
import designsystem.Spacing
import model.Progress
import model.WatchSnapshot

/**
 * The magazine home page: cover story, features, Continue watching beside a
 * pull-quote, then the rest of the library — a Compose port of
 * `home-view.js`'s section order. A part with nothing in it is not drawn at
 * all, the same rule the web follows.
 *
 * [rows] are the plain shelf rows that follow the magazine header — Latest
 * series and Latest courses; [magazine] already carries the cover,
 * features, resume strip and its own "Recently added" row, so [rows] must
 * not repeat Continue, Next up or the Movies shelf.
 */
@Composable
internal fun HomeScreen(
    magazine: MagazineHome,
    rows: List<HomeRow>,
    watch: WatchSnapshot,
    columns: Int,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onSeeAll: (shelf: String) -> Unit,
) {
    val positions = remember(watch) { watch.progress.associateBy { it.setId } }
    val watchedIds = remember(watch) { watch.watched.mapTo(HashSet()) { it.setId } }
    val editorial = magazine.editorial

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        if (editorial.cover.isNotEmpty()) {
            item(key = "cover", span = { GridItemSpan(maxLineSpan) }) {
                CoverStory(films = editorial.cover, onPlay = { onOpenTitle(it.setId) }, onOpenTitle = onOpenTitle)
            }
        }
        if (editorial.features.isNotEmpty()) {
            item(key = "features", span = { GridItemSpan(maxLineSpan) }) {
                FeatureStrip(
                    features = editorial.features,
                    onOpenTitle = onOpenTitle,
                    modifier = Modifier.padding(horizontal = Spacing.medium),
                )
            }
        }
        if (magazine.resumeCards.isNotEmpty()) {
            item(key = "resume", span = { GridItemSpan(maxLineSpan) }) {
                Column(modifier = Modifier.padding(top = Spacing.medium)) {
                    SectionHeading(title = "Continue watching", onSeeAll = { onSeeAll("Continue") })
                    ResumeStrip(cards = magazine.resumeCards, onOpenTitle = onOpenTitle, modifier = Modifier.padding(top = Spacing.small))
                }
            }
        }
        val quote = editorial.quote
        if (quote != null) {
            item(key = "quote", span = { GridItemSpan(maxLineSpan) }) {
                PullQuote(set = quote, onOpenTitle = onOpenTitle)
            }
        }

        homeRow(magazine.recentlyAddedRow, positions, watchedIds, onOpenTitle, onOpenCollection, onSeeAll)
        for (row in rows) homeRow(row, positions, watchedIds, onOpenTitle, onOpenCollection, onSeeAll)
    }
}

private fun LazyGridScope.homeRow(
    row: HomeRow,
    positions: Map<String, Progress>,
    watchedIds: Set<String>,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onSeeAll: (shelf: String) -> Unit,
) {
    if (row.total == 0) return
    item(key = row.title, span = { GridItemSpan(maxLineSpan) }) {
        RowHeading(row = row, onSeeAll = row.seeAll?.let { target -> { onSeeAll(target) } })
    }
    when (val content = row.content) {
        is RowContent.Entries -> {
            items(items = content.entries, key = { "${row.title}/${keyOf(it)}" }) { entry ->
                EntryCard(entry, positions, watchedIds, onOpenTitle, onOpenCollection)
            }
        }

        is RowContent.Sets -> {
            items(items = content.cards, key = { "${row.title}/${it.set.setId}" }) { card ->
                SetPlate(card = card, modifier = Modifier, onClick = { onOpenTitle(card.set.setId) })
            }
        }
    }
}

@Composable
private fun SectionHeading(
    title: String,
    onSeeAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.medium),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "See all",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier
                    .clickable(role = Role.Button, onClick = onSeeAll)
                    .padding(start = Spacing.medium, top = Spacing.small, bottom = Spacing.small),
        )
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
private fun RowHeading(
    row: HomeRow,
    onSeeAll: (() -> Unit)?,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.medium)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            // The same " · n" the web player's row heading carries.
            Text(
                text =
                    buildAnnotatedString {
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
                    modifier =
                        Modifier
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

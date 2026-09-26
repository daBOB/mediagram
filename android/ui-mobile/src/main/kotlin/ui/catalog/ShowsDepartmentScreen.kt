package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import catalog.Entry
import catalog.SetCard
import catalog.ShowsDepartment
import catalog.extentOf
import catalog.firstItemOf
import catalog.resumeLine
import catalog.watchedFractionOf
import designsystem.Spacing
import model.WatchSnapshot

private val DEPT_CARD_WIDTH = 140.dp

/**
 * The Series or Tutorials department's opening page — a Compose port of
 * `department-pages.js#renderShowsDept`: a hero, the resume strip for what
 * is underway, Popular/New rows once there are enough shows for a row to be
 * a selection rather than the whole shelf, then every show or course.
 *
 * @param label "Series" or "Tutorials", both the hero's title and what the
 *   Continue row and the foot section call it.
 * @param unit "episode" or "lesson", for the hero's own count line.
 */
@Composable
internal fun ShowsDepartmentScreen(
    label: String,
    unit: String,
    department: ShowsDepartment,
    watch: WatchSnapshot,
    heldIds: Set<String>,
    columns: Int,
    onOpenTitle: (String) -> Unit,
    onOpenCollection: (String) -> Unit,
) {
    val positions = remember(watch) { watch.progress.associateBy { it.setId } }
    val watchedIds = remember(watch) { watch.watched.mapTo(HashSet()) { it.setId } }
    val resumeCards = remember(department.underway, positions, watchedIds, heldIds) {
        buildList {
            for (set in department.underway.continues) {
                add(SetCard(set, resumeLine(positions[set.setId]), watchedFractionOf(positions[set.setId]), set.setId in watchedIds, set.setId in heldIds))
            }
            for (entry in department.underway.nextUp) {
                val caption = if (entry.resume) resumeLine(positions[entry.set.setId]) else "Next up"
                add(SetCard(entry.set, caption, watchedFractionOf(positions[entry.set.setId]), entry.set.setId in watchedIds, entry.set.setId in heldIds))
            }
        }
    }
    val leadTitle = department.lead?.let { firstItemOf(it.divisions) }
    val leadKey = department.lead?.key

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.large),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        item(key = "hero", span = { GridItemSpan(maxLineSpan) }) {
            DepartmentHero(
                kicker = "Only in your library",
                title = label,
                line = "${countOf(department.showCount, if (label == "Series") "show" else "course")} · ${countOf(department.itemCount, unit)}",
                lead = leadTitle,
                onOpenTitle = { if (leadKey != null) onOpenCollection(leadKey) },
            )
        }
        if (leadTitle != null && !leadTitle.tagline.isNullOrEmpty()) {
            item(key = "quote", span = { GridItemSpan(maxLineSpan) }) {
                PullQuote(set = leadTitle, onOpenTitle = { if (leadKey != null) onOpenCollection(leadKey) })
            }
        }
        if (resumeCards.isNotEmpty()) {
            item(key = "continue-heading", span = { GridItemSpan(maxLineSpan) }) {
                DeptRowHeading(title = if (label == "Series") "Continue your series" else "Continue your courses")
            }
            item(key = "continue", span = { GridItemSpan(maxLineSpan) }) {
                ResumeStrip(cards = resumeCards, onOpenTitle = onOpenTitle)
            }
        }
        if (department.popular.isNotEmpty()) {
            item(key = "popular-heading", span = { GridItemSpan(maxLineSpan) }) {
                DeptRowHeading(title = if (label == "Series") "Popular series" else "Popular courses")
            }
            item(key = "popular", span = { GridItemSpan(maxLineSpan) }) {
                CollectionRow(department.popular, onOpenCollection)
            }
        }
        if (department.newEpisodes.isNotEmpty()) {
            item(key = "new-heading", span = { GridItemSpan(maxLineSpan) }) {
                DeptRowHeading(title = "New ${unit}s")
            }
            item(key = "new", span = { GridItemSpan(maxLineSpan) }) {
                CollectionRow(department.newEpisodes, onOpenCollection)
            }
        }
        item(key = "all-heading", span = { GridItemSpan(maxLineSpan) }) {
            DeptRowHeading(title = if (label == "Series") "All shows" else "All courses")
        }
        items(items = department.all, key = { "all/${it.key}" }) { entry ->
            EntryCard(entry, positions, watchedIds, onOpenTitle, onOpenCollection, heldIds)
        }
    }
}

@Composable
private fun CollectionRow(shows: List<Entry.Collection>, onOpenCollection: (String) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        contentPadding = PaddingValues(horizontal = Spacing.medium),
    ) {
        lazyRowItems(items = shows, key = { it.key }) { entry ->
            PosterCard(
                posterPath = entry.posterPath,
                title = entry.name,
                caption = extentOf(entry),
                modifier = Modifier.width(DEPT_CARD_WIDTH),
                onClick = { onOpenCollection(entry.key) },
            )
        }
    }
}

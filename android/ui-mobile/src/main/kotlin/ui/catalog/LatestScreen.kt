package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import catalog.Department
import catalog.Shelf
import catalog.keyOf
import catalog.latestOf
import designsystem.Spacing
import model.WatchSnapshot
import ui.catalog.home.CourseList

private const val LATEST_LIMIT = 48

/**
 * Films, shows and courses, newest arrival first — a Compose port of
 * `utility-pages.js#renderLatest`. Built from the same [latestOf] the start
 * page's own Latest bands are, each under its department's own label — the
 * web heads them `SECTIONS.*.label` ("Movies", "Series", "Tutorials"), not
 * Home's "Latest films" wording. Courses are a list, not plates, as the web
 * asks `collectionGrid` for `{mode: LIST}`: a course carries no artwork to
 * fill a plate with.
 */
@Composable
internal fun LatestScreen(
    shelves: List<Shelf>,
    watch: WatchSnapshot,
    heldIds: Set<String>,
    columns: Int,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
) {
    val latest = remember(shelves) { latestOf(shelves, posterLimit = LATEST_LIMIT, limit = LATEST_LIMIT) }
    val positions = remember(watch) { watch.progress.associateBy { it.setId } }
    val watchedIds = remember(watch) { watch.watched.mapTo(HashSet()) { it.setId } }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.large),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            ShelfHead(title = "Latest", sub = "Newest arrivals first")
        }
        for ((department, entries) in listOf(Department.MOVIES to latest.movies, Department.SERIES to latest.series)) {
            if (entries.isEmpty()) continue
            shelfSub(department.label)
            items(items = entries, key = { "${department.label}/${keyOf(it)}" }) { entry ->
                EntryCard(entry, positions, watchedIds, onOpenTitle, onOpenCollection, heldIds)
            }
        }
        if (latest.courses.isNotEmpty()) {
            shelfSub(Department.TUTORIALS.label)
            // One item, not one per course: the grid's own gap would open
            // between rows the list draws edge to edge. Capped at
            // [LATEST_LIMIT], so never long enough to need laziness.
            item(key = "courses", span = { GridItemSpan(maxLineSpan) }) {
                CourseList(latest.courses, onOpenCollection)
            }
        }
    }
}

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
import catalog.Entry
import catalog.RowContent
import catalog.Shelf
import catalog.homeRowsOf
import catalog.keyOf
import designsystem.Spacing
import model.WatchSnapshot
import ui.catalog.home.CourseList

private const val LATEST_LIMIT = 48

/**
 * The home's Latest rows this page draws, each under the department's own
 * name — the web heads them `SECTIONS.*.label` ("Movies", "Series",
 * "Tutorials"), not the home rows' "Latest films" wording.
 */
private val LATEST_HEADINGS = mapOf("Latest films" to "Movies", "Latest series" to "Series", "Latest courses" to "Tutorials")

/**
 * Films, shows and courses, newest arrival first — a Compose port of
 * `utility-pages.js#renderLatest`. Built from the same [homeRowsOf] the
 * start page's own Latest rows already are, kept to the three that name
 * one, rather than a second "newest by kind" computation of its own.
 * Courses are a list, not plates, as the web asks `collectionGrid` for
 * `{mode: LIST}`: a course carries no artwork to fill a plate with.
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
    val rows = remember(shelves, watch, heldIds) {
        homeRowsOf(shelves, watch, heldIds, limit = LATEST_LIMIT).filter { it.title in LATEST_HEADINGS }
    }
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
        for (row in rows) {
            val content = row.content as? RowContent.Entries ?: continue
            if (content.entries.isEmpty()) continue
            val heading = LATEST_HEADINGS.getValue(row.title)
            shelfSub(heading)
            if (heading == "Tutorials") {
                // One item, not one per course: the grid's own gap would open
                // between rows the list draws edge to edge. Capped at
                // [LATEST_LIMIT], so never long enough to need laziness.
                item(key = "courses", span = { GridItemSpan(maxLineSpan) }) {
                    CourseList(content.entries.filterIsInstance<Entry.Collection>(), onOpenCollection)
                }
            } else {
                items(items = content.entries, key = { "${row.title}/${keyOf(it)}" }) { entry ->
                    EntryCard(entry, positions, watchedIds, onOpenTitle, onOpenCollection, heldIds)
                }
            }
        }
    }
}

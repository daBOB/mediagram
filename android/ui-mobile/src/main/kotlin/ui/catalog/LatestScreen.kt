package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import catalog.Department
import catalog.Latest
import catalog.factsLine
import catalog.keyOf
import designsystem.Spacing
import kotlinx.coroutines.launch
import model.MediaSet
import model.WatchSnapshot
import ui.catalog.home.CourseList

/**
 * Films, shows, documentaries and courses, newest arrival first — a Compose
 * port of `utility-pages.js#renderLatest`, each under its department's own
 * label — the web heads them `SECTIONS.*.label` ("Movies", "Series",
 * "Documentaries", "Tutorials"), not Home's "Latest films" wording — with a
 * line of those labels under the heading that jumps to each, as the web's
 * `jumpNav` does. Courses are a list, not plates, as the web asks
 * `collectionGrid` for `{mode: LIST}`: a course carries no artwork to fill a
 * plate with. A documentary plays from its plate ([onPlay]), as on the
 * Documentaries page: it has no page of its own.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LatestScreen(
    latest: Latest,
    watch: WatchSnapshot,
    heldIds: Set<String>,
    columns: Int,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onPlay: (setId: String) -> Unit = {},
) {
    val positions = remember(watch) { watch.progress.associateBy { it.setId } }
    val watchedIds = remember(watch) { watch.watched.mapTo(HashSet()) { it.setId } }
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    // Each part's heading's place in the grid below: the header first, then
    // each part's heading and its items, the courses one item between them.
    val parts =
        remember(latest) {
            var index = 1
            listOf(
                Department.MOVIES.label to latest.movies.size,
                Department.SERIES.label to latest.series.size,
                Department.DOCUMENTARIES.label to latest.documentaries.size,
                Department.TUTORIALS.label to if (latest.courses.isEmpty()) 0 else 1,
            ).filter { it.second > 0 }.map { (label, size) -> (label to index).also { index += 1 + size } }
        }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = grid,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.large),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            Column {
                ShelfHead(title = "Latest", sub = "Newest arrivals first")
                if (parts.size > 1) {
                    FlowRow(
                        modifier = Modifier.semantics { contentDescription = "Jump to" },
                        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                        verticalArrangement = Arrangement.spacedBy(Spacing.small),
                    ) {
                        for ((label, index) in parts) PagePill(label, onClick = { scope.launch { grid.animateScrollToItem(index) } })
                    }
                }
            }
        }
        for ((department, entries) in listOf(Department.MOVIES to latest.movies, Department.SERIES to latest.series)) {
            if (entries.isEmpty()) continue
            shelfSub(department.label)
            items(items = entries, key = { "${department.label}/${keyOf(it)}" }) { entry ->
                EntryCard(entry, positions, watchedIds, onOpenTitle, onOpenCollection, heldIds)
            }
        }
        if (latest.documentaries.isNotEmpty()) {
            shelfSub(Department.DOCUMENTARIES.label)
            items(items = latest.documentaries, key = { "${Department.DOCUMENTARIES.label}/${it.setId}" }) { set ->
                DocumentaryPlate(set, set.setId in watchedIds, onPlay)
            }
        }
        if (latest.courses.isNotEmpty()) {
            shelfSub(Department.TUTORIALS.label)
            // One item, not one per course: the grid's own gap would open
            // between rows the list draws edge to edge. Capped by the
            // Latest page's own limit, so never long enough to need laziness.
            item(key = "courses", span = { GridItemSpan(maxLineSpan) }) {
                CourseList(latest.courses, onOpenCollection)
            }
        }
    }
}

@Composable
private fun DocumentaryPlate(set: MediaSet, watched: Boolean, onPlay: (setId: String) -> Unit) {
    PosterCard(
        posterPath = set.posterPath,
        title = set.title,
        caption = factsLine(set.year, set.durationSecs),
        watched = watched,
        modifier = Modifier,
        onClick = { onPlay(set.setId) },
    )
}

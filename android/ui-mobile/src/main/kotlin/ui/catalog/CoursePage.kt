package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import catalog.Entry
import catalog.courseExtentOf
import catalog.rowsOf
import designsystem.Spacing
import model.WatchSnapshot
import ui.common.catalog.togetherJoinIn

/**
 * A course's own page, as the web's `course-view.js` draws one: the page's
 * shelf head — the course's name, how many lessons and documents it holds,
 * a rule — over its lessons, each folder headed and indented as deep as it
 * sits. The television's course page is the same index.
 *
 * No art, rating, genres or overview above the lessons: the web's course
 * page has none, and a course carries no provider entry to draw them from.
 * It has no Play of its own either, so while a watch-together session plays
 * one of its lessons Join stands under the head, as on the web.
 */
@Composable
internal fun CoursePage(
    collection: Entry.Collection,
    watch: WatchSnapshot,
    heldIds: Set<String>,
    onPlay: (setId: String) -> Unit,
    state: LazyListState,
) {
    // Flattened once per collection, not on every recomposition: the depth
    // becomes an indent here because a lazy list cannot nest, and a viewer
    // still has to see which folder holds what.
    val rows = remember(collection) { rowsOf(collection.divisions) }
    val extent = remember(collection) { courseExtentOf(collection.divisions) }
    val positions = remember(watch) { watch.progress.associateBy { it.setId } }
    val watchedIds = remember(watch) { watch.watched.mapTo(HashSet()) { it.setId } }
    val join = togetherJoinIn(collection)
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        item(key = "head") { ShelfHead(title = collection.name, sub = extent) }
        join?.let { item(key = "join") { PlayPill("Join", it.join) } }
        items(rows, positions, watchedIds, heldIds, onPlay)
    }
}

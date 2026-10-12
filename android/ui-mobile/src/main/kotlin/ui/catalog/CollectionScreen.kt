package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import catalog.CollectionKind
import catalog.Entry
import catalog.Shelf
import catalog.courseExtentOf
import catalog.rowsOf
import data.PortraitRequestLog
import designsystem.Spacing
import model.TitleCredits
import model.WatchSnapshot
import uniffi.mediagram_core.TitleInfo

/**
 * What is inside one show or course: a show's own feature-article page
 * ([SeriesPage]) when [collection] is one, else a course's index
 * ([CoursePage]) — its chapters and lessons, flattened once and shown as
 * one indented list.
 *
 * Parameters past [onOpenGenre] default to inert values so a test composes
 * the page with only what it checks; `LibraryTitleBranches` wires every
 * one — the same rule as [TitleDetailScreen]'s on the film side.
 * [state] is the page's list, hoisted as a department page's is.
 */
@Composable
fun CollectionScreen(
    collection: Entry.Collection,
    info: TitleInfo?,
    watch: WatchSnapshot,
    heldIds: Set<String>,
    onOpenTitle: (setId: String) -> Unit,
    onOpenGenre: (String) -> Unit,
    shelves: List<Shelf> = emptyList(),
    onOpenCollection: (key: String) -> Unit = {},
    onOpenPerson: (Long) -> Unit = {},
    onPlay: (setId: String) -> Unit = {},
    editorsChoice: String? = null,
    onToggleEditorsChoice: (() -> Unit)? = null,
    onToggleWatchlist: () -> Unit = {},
    titleCredits: suspend (String) -> TitleCredits = { TitleCredits.Empty },
    fetchPortrait: suspend (Long) -> String? = { null },
    portraits: PortraitRequestLog = PortraitRequestLog(),
    season: String? = null,
    onSelectSeason: (String) -> Unit = {},
    state: LazyListState = rememberLazyListState(),
) {
    if (collection.kind == CollectionKind.SHOW) {
        SeriesPage(
            collection, info, watch, heldIds, onOpenTitle, onOpenGenre, shelves, onOpenCollection,
            onOpenPerson, onPlay, editorsChoice, onToggleEditorsChoice, onToggleWatchlist,
            titleCredits, fetchPortrait, portraits, season, onSelectSeason, state,
        )
        return
    }
    CoursePage(collection, watch, heldIds, onPlay, state)
}

/**
 * A course's own page, as the web's `course-view.js` draws one: the page's
 * shelf head — the course's name, how many lessons and documents it holds,
 * a rule — over its lessons, each folder headed and indented as deep as it
 * sits. The television's course page is the same index.
 *
 * No art, rating, genres or overview above the lessons: the web's course
 * page has none, and a course carries no provider entry to draw them from.
 */
@Composable
private fun CoursePage(
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
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        item(key = "head") { ShelfHead(title = collection.name, sub = extent) }
        items(rows, positions, watchedIds, heldIds, onPlay)
    }
}

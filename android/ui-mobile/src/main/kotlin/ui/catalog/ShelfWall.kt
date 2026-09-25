package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.window.core.layout.WindowWidthSizeClass
import catalog.CollectionKind
import catalog.Entry
import catalog.Shelf
import catalog.offersViewChoice
import catalog.shelfViewFor
import designsystem.Spacing
import model.Progress
import model.WatchSnapshot
import settings.ShelfView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import catalog.Page
import catalog.pageOf
import catalog.pickFeatured
import model.MediaSet
import kotlin.random.Random

/**
 * Everything one catalog shelf holds, on one wall, in one direction of
 * travel — split out of `CatalogScreen.kt` once the masthead grew past the
 * three shelves this draws, so that file stays about tab routing and this
 * one stays about the grid.
 *
 * Not a side-scrolling rail. A rail hides how much is on a shelf and puts
 * whatever it happens to show first in front of everything behind it, which
 * is how a storefront ranks stock. This library is finite and already
 * owned, so all of it is on the page and none of it is ranked.
 *
 * The column count comes from the window's width class rather than from how
 * many titles the shelf holds, so a shelf with one course in it keeps a
 * plate the size of a plate instead of stretching one across the width and
 * saying something untrue about how much is there.
 */
@Composable
internal fun ShelfWall(
    shelf: Shelf,
    watch: WatchSnapshot,
    heldIds: Set<String>,
    columns: Int,
    view: ShelfViewChoice,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    films: FilmShelfActions? = null,
) {
    val positions = remember(watch) { watch.progress.associateBy { it.setId } }
    val watchedIds = remember(watch) { watch.watched.mapTo(HashSet()) { it.setId } }
    // Films are paged, as the web's Movies shelf is; shows and courses are not.
    val filmShelf = shelf.entries.isNotEmpty() && shelf.entries.all { it is Entry.Film }
    var requested by rememberSaveable(shelf.title) { mutableIntStateOf(1) }
    val shown = if (filmShelf) pageOf(shelf.entries, requested) else Page(shelf.entries, 1, 1)
    val pager: @Composable () -> Unit = { ShelfPager(shown.page, shown.pages, onPage = { requested = it }) }
    var reel by remember { mutableStateOf<List<MediaSet>?>(null) }
    val filmSets = remember(shelf) { shelf.entries.filterIsInstance<Entry.Film>().map { it.set } }
    val featurable = films != null && filmShelf && pickFeatured(filmSets, watchedIds, Random, 1).isNotEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        if (offersViewChoice(shelf)) {
            ShelfBar(
                pageLine = "page ${shown.page} of ${shown.pages}".takeIf { shown.pages > 1 },
                view = view,
                onFeatured = { reel = pickFeatured(filmSets, watchedIds, Random) }.takeIf { featurable },
            )
        }
        if (shelfViewFor(shelf, view.chosen) == ShelfView.LIST) {
            ShelfList(shown.items, positions, watchedIds, heldIds, onOpenTitle, onOpenCollection, footer = pager, page = shown.page)
            return@Column
        }
        val grid = rememberLazyGridState()
        ScrollToTopOnNewPage(shown.page) { grid.scrollToItem(0) }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = grid,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            items(items = shown.items, key = ::keyOf) { entry ->
                EntryCard(entry, positions, watchedIds, onOpenTitle, onOpenCollection, heldIds)
            }
            item(key = "pager", span = { GridItemSpan(maxLineSpan) }) { pager() }
        }
    }
    reel?.let { picks ->
        FeaturedReel(
            films = picks,
            titleInfo = films?.titleInfo ?: { null },
            onPlay = { films?.onPlay?.invoke(it) },
            onDetails = { onOpenTitle(it.setId) },
            onClose = { reel = null },
        )
    }
}


/** This device's shelf view and the way to change it, handed down as one. */
internal data class ShelfViewChoice(val chosen: ShelfView, val onChoose: (ShelfView) -> Unit)

/** One shelf card: a film's poster, or a show's or a course's. Shared with the Kids wall. */
@Composable
internal fun EntryCard(
    entry: Entry,
    positions: Map<String, Progress>,
    watchedIds: Set<String>,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    heldIds: Set<String> = emptySet(),
) {
    when (entry) {
        // A film opens the screen that describes it; a show or a
        // course opens what is inside it, because the plate stands
        // for everything there and there is no one thing it could
        // sensibly start.
        is Entry.Film -> PosterCard(
            posterPath = entry.set.posterPath,
            title = entry.set.title,
            // The year and the runtime, in the figures the detail
            // screen already sets them in. A shelf of three hundred
            // films with nothing but names under them is a wall of
            // artwork; the line under the name is what tells two
            // versions of the same title apart.
            caption = factsLine(entry.set.year, entry.set.durationSecs),
            progress = watchedFractionOf(positions[entry.set.setId]),
            watched = entry.set.setId in watchedIds,
            held = entry.set.setId in heldIds,
            modifier = Modifier,
            onClick = { onOpenTitle(entry.set.setId) },
        )

        // No mark of its own, same as the web's `collectionGrid`: a
        // show or a course is not one title to finish.
        is Entry.Collection -> PosterCard(
            posterPath = entry.posterPath,
            title = entry.name,
            caption = extentOf(entry),
            modifier = Modifier,
            onClick = { onOpenCollection(entry.key) },
        )
    }
}

internal fun keyOf(entry: Entry): String = when (entry) {
    is Entry.Film -> entry.set.setId
    is Entry.Collection -> entry.key
}

/**
 * What the plate counts in. A catalogue says "12 episodes", not "12 items",
 * and a course is measured in the chapters a viewer will work through
 * rather than in its total number of videos.
 */
internal fun extentOf(collection: Entry.Collection): String = when (collection.kind) {
    CollectionKind.SHOW -> "${collection.count} ${plural(collection.count, "episode")}"
    CollectionKind.COURSE -> "${collection.chapters} ${plural(collection.chapters, "chapter")}"
}

private fun plural(count: Int, word: String): String = if (count == 1) word else "${word}s"

/** A tablet fits more plates across the page than a phone does. */
internal fun posterColumnsFor(widthSizeClass: WindowWidthSizeClass): Int = when (widthSizeClass) {
    WindowWidthSizeClass.EXPANDED -> 6
    WindowWidthSizeClass.MEDIUM -> 4
    else -> 3
}

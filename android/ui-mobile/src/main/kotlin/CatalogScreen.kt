package ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.window.core.layout.WindowWidthSizeClass
import catalog.CatalogUiState
import catalog.CollectionKind
import catalog.Entry
import catalog.Shelf
import designsystem.Spacing

/**
 * The shelves, and one line above them while the library is being worked
 * on. [fetching] is the other run that changes what is on these shelves —
 * it fills in the artwork on them, and the descriptions behind them — and
 * it is reported here rather than beside itself, because a viewer watching
 * something happen should not have to learn a second vocabulary for it
 * depending on which menu item started it.
 */
@Composable
fun CatalogScreen(
    state: CatalogUiState,
    fetching: Boolean,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
) {
    when (state) {
        CatalogUiState.Loading -> CenteredMessage("Loading your library…")
        CatalogUiState.Empty -> CenteredMessage("The library is empty.")
        is CatalogUiState.Failed -> CenteredMessage(state.message)
        is CatalogUiState.Ready -> Shelves(state, fetching, onOpenTitle, onOpenCollection)
    }
}

/**
 * One shelf on screen, chosen from the masthead above it.
 *
 * The shelf a viewer was last on is kept across a rotation and a process
 * death, because coming back to the top of the film shelf after glancing
 * at something else is the kind of small forgetting that makes an app feel
 * like it is not paying attention.
 */
@Composable
private fun Shelves(
    state: CatalogUiState.Ready,
    fetching: Boolean,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
) {
    val shelves = state.shelves
    if (shelves.isEmpty()) {
        CenteredMessage("The library is empty.")
        return
    }
    var chosen by rememberSaveable { mutableIntStateOf(0) }
    // A refresh can return a library with fewer shelves than the one that
    // was on screen when it started.
    val selected = chosen.coerceIn(0, shelves.lastIndex)

    Column(modifier = Modifier.fillMaxSize()) {
        // Pinned above the wall rather than scrolling inside it: it reports
        // on the whole library, not on a row of it, and a viewer who has
        // scrolled down is exactly the one who would otherwise watch the
        // shelves change under their thumb with nothing having said why.
        if (state.refreshing || fetching) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        ShelfTabs(shelves = shelves, selected = selected, onSelect = { chosen = it })
        // Above the shelf, not instead of it: the library below is the one
        // that was on this device before the refresh was tried, and it is
        // still every bit of it.
        state.notice?.let { notice ->
            Text(
                text = notice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.small),
            )
        }
        ShelfWall(shelves[selected], onOpenTitle, onOpenCollection)
    }
}

/**
 * Everything one shelf holds, on one wall, in one direction of travel.
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
private fun ShelfWall(
    shelf: Shelf,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
) {
    val columns = posterColumnsFor(currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        items(items = shelf.entries, key = ::keyOf) { entry ->
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

private fun keyOf(entry: Entry): String = when (entry) {
    is Entry.Film -> entry.set.setId
    is Entry.Collection -> entry.key
}

/**
 * What the plate counts in. A catalogue says "12 episodes", not "12 items",
 * and a course is measured in the chapters a viewer will work through
 * rather than in its total number of videos.
 */
private fun extentOf(collection: Entry.Collection): String = when (collection.kind) {
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

/**
 * What the screen says when it has nothing to show.
 *
 * Set in the catalogue's own reading face rather than left at the default,
 * because a first run, an empty library and a failed load are the three
 * moments a viewer reads a whole sentence here, and they are exactly the
 * moments the app would otherwise stop sounding like itself.
 */
@Composable
private fun CenteredMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(Spacing.extraLarge),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

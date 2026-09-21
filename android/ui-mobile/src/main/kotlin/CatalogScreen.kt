package ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        is CatalogUiState.Ready -> Column(modifier = Modifier.fillMaxSize()) {
            // Pinned above the list rather than scrolling inside it: it
            // reports on the whole library, not on a row of it, and a
            // viewer who has scrolled down is exactly the one who would
            // otherwise watch the shelves change under their thumb with
            // nothing having said why.
            if (state.refreshing || fetching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            ShelfList(state, onOpenTitle, onOpenCollection)
        }
    }
}

@Composable
private fun ShelfList(
    state: CatalogUiState.Ready,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
) {
    val columns = posterColumnsFor(currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
        contentPadding = PaddingValues(Spacing.medium),
    ) {
        // Above the shelves, not instead of them: the library below is the
        // one that was on this device before the refresh was tried, and it
        // is still every bit of it.
        state.notice?.let { notice ->
            item(key = "notice") {
                Text(
                    text = notice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        items(state.shelves, key = { it.title }) { shelf ->
            ShelfRow(shelf, columns, onOpenTitle, onOpenCollection)
        }
    }
}

@Composable
private fun ShelfRow(
    shelf: Shelf,
    columns: Int,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
) {
    Column {
        Text(text = shelf.title, style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            items(shelf.entries, key = ::keyOf) { entry ->
                val modifier = Modifier.fillParentMaxWidth(1f / columns)
                when (entry) {
                    // A film opens the screen that describes it; a show or a
                    // course opens what is inside it, because the card
                    // stands for everything there and there is no one thing
                    // it could sensibly start.
                    is Entry.Film -> PosterCard(
                        posterPath = entry.set.posterPath,
                        title = entry.set.title,
                        caption = null,
                        modifier = modifier,
                        onClick = { onOpenTitle(entry.set.setId) },
                    )

                    is Entry.Collection -> PosterCard(
                        posterPath = entry.posterPath,
                        title = entry.name,
                        caption = extentOf(entry),
                        modifier = modifier,
                        onClick = { onOpenCollection(entry.key) },
                    )
                }
            }
        }
    }
}

private fun keyOf(entry: Entry): String = when (entry) {
    is Entry.Film -> entry.set.setId
    is Entry.Collection -> entry.key
}

/**
 * What the card counts in. A catalogue says "12 episodes", not "12 items",
 * and a course is measured in the chapters a viewer will work through
 * rather than in its total number of videos.
 */
private fun extentOf(collection: Entry.Collection): String = when (collection.kind) {
    CollectionKind.SHOW -> "${collection.count} ${plural(collection.count, "episode")}"
    CollectionKind.COURSE -> "${collection.chapters} ${plural(collection.chapters, "chapter")}"
}

private fun plural(count: Int, word: String): String = if (count == 1) word else "${word}s"

/** A tablet shows more posters per visible row than a phone does. */
internal fun posterColumnsFor(widthSizeClass: WindowWidthSizeClass): Int = when (widthSizeClass) {
    WindowWidthSizeClass.EXPANDED -> 6
    WindowWidthSizeClass.MEDIUM -> 4
    else -> 3
}

@Composable
private fun CenteredMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = message)
    }
}

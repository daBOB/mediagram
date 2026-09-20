package ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.window.core.layout.WindowWidthSizeClass
import catalog.CatalogUiState
import catalog.CollectionKind
import catalog.Entry
import catalog.Shelf
import coil3.compose.AsyncImage
import designsystem.Spacing
import java.io.File

@Composable
fun CatalogScreen(
    state: CatalogUiState,
    onPlay: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
) {
    when (state) {
        CatalogUiState.Loading -> CenteredMessage("Loading your library…")
        CatalogUiState.Empty -> CenteredMessage("The library is empty.")
        is CatalogUiState.Failed -> CenteredMessage(state.message)
        is CatalogUiState.Ready -> ShelfList(state, onPlay, onOpenCollection)
    }
}

@Composable
private fun ShelfList(
    state: CatalogUiState.Ready,
    onPlay: (setId: String) -> Unit,
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
            ShelfRow(shelf, columns, onPlay, onOpenCollection)
        }
    }
}

@Composable
private fun ShelfRow(
    shelf: Shelf,
    columns: Int,
    onPlay: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
) {
    Column {
        Text(text = shelf.title, style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            items(shelf.entries, key = ::keyOf) { entry ->
                val modifier = Modifier.fillParentMaxWidth(1f / columns)
                when (entry) {
                    // A film plays; a show or a course opens, because the
                    // card stands for everything inside it and there is no
                    // one thing it could sensibly start.
                    is Entry.Film -> PosterCard(
                        posterPath = entry.set.posterPath,
                        title = entry.set.title,
                        caption = null,
                        modifier = modifier,
                        onClick = { onPlay(entry.set.setId) },
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

/**
 * A card is named underneath rather than across its face.
 *
 * Every episode of a show carries the same artwork, and the index pinned in
 * a channel carries no artwork at all, so the face is the least reliable
 * place to say what something is. Initials stand in for a missing poster —
 * enough to tell two cards apart at a glance, and the name is right below
 * them either way.
 */
@Composable
private fun PosterCard(
    posterPath: String?,
    title: String,
    caption: String?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(modifier = modifier) {
        Card(modifier = Modifier.aspectRatio(2f / 3f).clickable(onClick = onClick)) {
            if (posterPath != null) {
                AsyncImage(
                    model = File(posterPath),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = initialsOf(title),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(Spacing.small),
                    )
                }
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.extraSmall),
        )
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Two letters to stand in for artwork that is not there. */
internal fun initialsOf(title: String): String = title
    .split(WHITESPACE)
    .take(2)
    .mapNotNull { word -> word.firstOrNull(Char::isLetterOrDigit) }
    .joinToString("")
    .uppercase()
    .ifEmpty { "?" }

private val WHITESPACE = Regex("\\s+")

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

package ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.window.core.layout.WindowWidthSizeClass
import catalog.CatalogUiState
import catalog.Shelf
import coil3.compose.AsyncImage
import designsystem.Spacing
import model.MediaSet
import java.io.File

@Composable
fun CatalogScreen(state: CatalogUiState, onOpen: (setId: String) -> Unit, onStartOver: () -> Unit) {
    when (state) {
        CatalogUiState.Loading -> CenteredMessage("Loading your library…")
        CatalogUiState.Empty -> CenteredMessage("The library is empty.")
        // A refresh that will not work is the one place this screen is a
        // dead end — a session Telegram invalidated elsewhere still reads
        // as signed in here, and nothing else on this screen leads back to
        // signing in again.
        is CatalogUiState.Failed -> FailedCatalog(state.message, onStartOver)
        is CatalogUiState.Ready -> ShelfList(state.shelves, onOpen)
    }
}

@Composable
private fun FailedCatalog(message: String, onStartOver: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = message)
        StartOverAction(onConfirm = onStartOver)
    }
}

@Composable
private fun ShelfList(shelves: List<Shelf>, onOpen: (setId: String) -> Unit) {
    val columns = posterColumnsFor(currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
        contentPadding = PaddingValues(Spacing.medium),
    ) {
        items(shelves, key = { it.title }) { shelf -> ShelfRow(shelf, columns, onOpen) }
    }
}

@Composable
private fun ShelfRow(shelf: Shelf, columns: Int, onOpen: (setId: String) -> Unit) {
    Column {
        Text(text = shelf.title, style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            items(shelf.items, key = { it.setId }) { set ->
                PosterCard(set, modifier = Modifier.fillParentMaxWidth(1f / columns), onOpen = onOpen)
            }
        }
    }
}

@Composable
private fun PosterCard(set: MediaSet, modifier: Modifier, onOpen: (setId: String) -> Unit) {
    Card(modifier = modifier.aspectRatio(2f / 3f).clickable { onOpen(set.setId) }) {
        val posterPath = set.posterPath
        if (posterPath != null) {
            AsyncImage(
                model = File(posterPath),
                contentDescription = set.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = set.title, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

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

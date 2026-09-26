package ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import catalog.Entry
import catalog.PersonPage
import catalog.initialsOf
import coil3.compose.AsyncImage
import designsystem.Spacing
import model.WatchSnapshot
import java.io.File

private val FACE_SIZE = 96.dp

/**
 * A person's page: their portrait, their name, then the titles in *this*
 * profile's own library they appear in — a Compose port of
 * `cast.js#renderPerson`'s own, non-empty branch. [PersonPage] is already
 * resolved to films this profile can see, films then shows, per the web's
 * own order.
 */
@Composable
internal fun PersonScreen(
    page: PersonPage,
    portraitPath: String?,
    watch: WatchSnapshot,
    columns: Int,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
) {
    val total = page.films.size + page.shows.size
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
            Column {
                PersonFace(name = page.person.name, portraitPath = portraitPath, size = FACE_SIZE)
                Text(
                    text = page.person.name,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = Spacing.medium),
                )
                Text(
                    text = countOf(total, "title") + " in your library",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.medium),
                )
            }
        }
        if (page.films.isNotEmpty()) {
            sectionHeading("Films")
            items(items = page.films, key = { it.setId }) { set ->
                EntryCard(Entry.Film(set), positions, watchedIds, onOpenTitle, onOpenCollection)
            }
        }
        if (page.shows.isNotEmpty()) {
            sectionHeading("Series")
            items(items = page.shows, key = { it.key }) { entry ->
                EntryCard(entry, positions, watchedIds, onOpenTitle, onOpenCollection)
            }
        }
    }
}

private fun LazyGridScope.sectionHeading(label: String) {
    item(key = "heading-$label", span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = Spacing.small, bottom = Spacing.small),
        )
    }
}

/** A person's round portrait, or their initials when this device holds none — `cast.js`'s own `faceOf`. */
@Composable
internal fun PersonFace(name: String, portraitPath: String?, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (portraitPath != null) {
            AsyncImage(
                model = File(portraitPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = initialsOf(name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

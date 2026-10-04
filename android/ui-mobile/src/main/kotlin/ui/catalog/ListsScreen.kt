package ui.catalog

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import catalog.KeptKind
import catalog.listArtOf
import catalog.spelledCountOf
import designsystem.Spacing
import model.ListOfSets
import model.MediaSet

/**
 * The lists a viewer has built, as [CollectionsScreen]'s own
 * [LazyColumn][androidx.compose.foundation.lazy.LazyColumn] items: each a
 * large card pictured by its first pictured title, [columns] to a line and
 * each line its own item, then "＋ New list" — the
 * "Your lists" half of `collections-page.js#renderCollectionsPage`. Titles
 * are filed onto a list from the player's "Add to list" dialog, not from
 * here.
 *
 * With no lists at all, one line says so where the web leaves the section
 * empty above its button: on a phone an empty band under a heading reads as
 * something that failed to load.
 */
internal fun LazyListScope.listsSection(
    lists: List<ListOfSets>,
    setsById: Map<String, MediaSet>,
    columns: Int,
    onOpen: (id: String) -> Unit,
    onNewList: () -> Unit,
) {
    if (lists.isEmpty()) {
        // Not [CenteredMessage]: its own `fillMaxSize()` wants a bounded
        // height to centre within, which a `LazyColumn` item never has.
        item(key = "lists-empty") {
            Text(
                text = KeptKind.COLLECTIONS.empty,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(Spacing.medium),
            )
        }
    } else {
        tileLines("lists", lists, columns, ListOfSets::id, Modifier.padding(horizontal = Spacing.medium)) { list, modifier ->
            ArtTile(
                name = list.name,
                meta = spelledCountOf(list.items.size, "title"),
                art = listArtOf(list, setsById),
                aspectRatio = DESTINATION_ASPECT,
                onClick = { onOpen(list.id) },
                modifier = modifier,
                destination = true,
            )
        }
    }
    item(key = "new-list") {
        PagePill(text = "＋ New list", onClick = onNewList, modifier = Modifier.padding(start = Spacing.medium, top = 20.dp))
    }
}

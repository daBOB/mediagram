package ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import catalog.KeptKind
import designsystem.Spacing
import model.ListOfSets

/**
 * The lists a viewer has built, each a door to its own — `listsView` in
 * collections-view.js — as [CollectionsScreen]'s own [LazyColumn][androidx.compose.foundation.lazy.LazyColumn]
 * items rather than a scrollable list of its own: a phone screen short
 * enough to clip the franchise row already left "＋ New list" beyond a list
 * that never scrolled either. Titles are filed onto a list from the
 * player's "Add to list" dialog, not from here: `collection-add.js`'s
 * in-list search picker is out of scope for this phase (see the phase's
 * Requirements — only "New list", opening one, rename and delete are asked
 * for on this tab).
 */
internal fun LazyListScope.listsSection(
    lists: List<ListOfSets>,
    onOpen: (id: String) -> Unit,
    onNewList: () -> Unit,
) {
    if (lists.isEmpty()) {
        // Not [CenteredMessage]: its own `fillMaxSize()` wants a bounded
        // height to centre within, which a `LazyColumn` item never has —
        // the same trap `ui.catalog.home.CoverSlide`'s own doc comment
        // already names for `fillMaxHeight()`. Inline text reads fine here;
        // nothing above or below it competes for the same vertical space.
        item {
            Text(
                text = KeptKind.COLLECTIONS.empty,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(Spacing.medium),
            )
        }
    } else {
        items(items = lists, key = ListOfSets::id) { list ->
            ListRow(list = list, onClick = { onOpen(list.id) })
            HorizontalDivider()
        }
    }
    item {
        Text(
            text = "＋ New list",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onNewList)
                    .padding(Spacing.medium),
        )
    }
}

@Composable
private fun ListRow(
    list: ListOfSets,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = Spacing.medium, vertical = Spacing.medium),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(list.name, style = MaterialTheme.typography.titleMedium)
        Text(
            text = countLabel(list.items.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** `1 title` / `12 titles` — a plain count, the same convention [KeptWall]'s own heading reads by. */
private fun countLabel(count: Int): String = "$count ${if (count == 1) "title" else "titles"}"

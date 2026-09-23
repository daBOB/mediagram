package ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import catalog.KeptKind
import designsystem.Spacing
import model.ListOfSets

/**
 * The lists a viewer has built, each a door to its own — `listsView` in
 * collections-view.js. Titles are filed onto a list from the player's "Add
 * to list" dialog, not from here: `collection-add.js`'s in-list search
 * picker is out of scope for this phase (see the phase's Requirements —
 * only "New list", opening one, rename and delete are asked for on this
 * tab).
 */
@Composable
internal fun ListsScreen(
    lists: List<ListOfSets>,
    onOpen: (id: String) -> Unit,
    onCreate: (name: String) -> Unit,
) {
    var naming by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (lists.isEmpty()) {
            CenteredMessage(KeptKind.COLLECTIONS.empty)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().weight(1f),
                contentPadding = PaddingValues(horizontal = Spacing.medium),
            ) {
                items(items = lists, key = ListOfSets::id) { list ->
                    ListRow(list = list, onClick = { onOpen(list.id) })
                    HorizontalDivider()
                }
            }
        }
        Text(
            text = "＋ New list",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { naming = true }
                .padding(Spacing.medium),
        )
    }

    if (naming) {
        ListNameDialog(
            title = "Name for the list",
            confirmLabel = "Create",
            onConfirm = { name -> naming = false; onCreate(name) },
            onDismiss = { naming = false },
        )
    }
}

@Composable
private fun ListRow(list: ListOfSets, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.medium),
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

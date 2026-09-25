package ui.tv.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import catalog.KeptKind
import designsystem.Overscan
import designsystem.Spacing
import model.ListOfSets
import ui.tv.TvTextRow

/**
 * The lists a viewer has built, each a door to its own — `listsView` in
 * collections-view.js and the phone's `ListsScreen`, as one column of text
 * rows: a list has no artwork of its own to put on a plate, the same reason
 * Home sets courses as lines. "＋ New list" is the last row, where the
 * remote runs out of lists, and asks for a name with [TvListNameQuestion].
 *
 * Titles are filed onto a list from the player's "Add to list", not from
 * here, as on the phone.
 *
 * [restoreKey] is the id of the list a viewer opened, so coming back puts
 * the remote on that row; with none, or none that still exists, the first
 * row takes focus — "＋ New list" itself when there are no lists yet.
 */
@Composable
internal fun TvLists(
    lists: List<ListOfSets>,
    onOpen: (id: String) -> Unit,
    onCreate: (name: String) -> Unit,
    restoreKey: String? = null,
) {
    // Saveable, so a rotation or a process death mid-name comes back to the
    // question rather than to the rows under it.
    var naming by rememberSaveable { mutableStateOf(false) }
    if (naming) {
        TvListNameQuestion(
            onConfirm = { name ->
                naming = false
                onCreate(name)
            },
            onDismiss = { naming = false },
        )
        return
    }

    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    // One past the last list is "＋ New list", which is where an empty tab lands.
    val focusIndex = remember(lists, restoreKey) { lists.indexOfFirst { it.id == restoreKey }.coerceAtLeast(0) }
    val focusNew = lists.isEmpty()
    // Keyed on the restore key as well as the index it resolves to, as
    // TvWall's is: coming back from a different list means "go there" even
    // when that list sits at the index the remote was already on.
    LaunchedEffect(focusIndex, focusNew, restoreKey) {
        listState.scrollToItem(focusIndex)
        focus.requestFocus()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        if (focusNew) {
            item(key = "empty") { TvQuietLine(KeptKind.COLLECTIONS.empty) }
        }
        itemsIndexed(items = lists, key = { _, list -> list.id }) { index, list ->
            TvTextRow(
                text = "${list.name} · ${countLabel(list.items.size)}",
                onClick = { onOpen(list.id) },
                modifier = Modifier.fillMaxWidth(),
                focusRequester = focus.takeIf { index == focusIndex },
            )
        }
        item(key = "new") {
            TvTextRow(
                text = "＋ New list",
                onClick = { naming = true },
                modifier = Modifier.fillMaxWidth(),
                focusRequester = focus.takeIf { focusNew },
            )
        }
    }
}

/** `1 title` / `12 titles` — the phone's own count beside each list. */
private fun countLabel(count: Int): String = "$count ${if (count == 1) "title" else "titles"}"

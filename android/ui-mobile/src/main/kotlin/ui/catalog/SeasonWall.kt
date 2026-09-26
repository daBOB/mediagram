package ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import catalog.Division
import catalog.Entry
import catalog.rowsOf
import designsystem.Spacing
import model.WatchSnapshot

/**
 * The Episodes tab on a show's own page: a season picker over a readable
 * list of that season's episodes — a Compose port of `series-page.js`'s own
 * `episodes()`, replacing the season-poster wall that used to be this
 * screen's own front door. No picker at all for a single-season show, the
 * same "nothing to pick from" gate the old wall followed.
 *
 * A [LazyListScope] extension rather than its own scrollable composable: a
 * show's episode list can run to a few hundred rows, and nesting a second
 * lazily-scrolled list inside the series page's own would either crash on
 * unbounded height or clip to whichever bound won. Its rows join the page's
 * own [androidx.compose.foundation.lazy.LazyColumn] instead, the same way
 * [items] already does for a course.
 *
 * [season]/[onSelectSeason] are the caller's own [androidx.compose.runtime.saveable.rememberSaveable]
 * state rather than this function's own: the same season must still be
 * shown once a watch-state update recomposes the whole series page, the
 * finding this phase's tab work was asked to carry over to the season
 * choice too.
 */
internal fun LazyListScope.seriesEpisodes(
    collection: Entry.Collection,
    season: String?,
    onSelectSeason: (String) -> Unit,
    watch: WatchSnapshot,
    heldIds: Set<String>,
    onOpenTitle: (setId: String) -> Unit,
) {
    val shown = collection.divisions.find { it.title == season } ?: collection.divisions.firstOrNull() ?: return
    if (collection.divisions.size > 1) {
        item(key = "season-picker") { SeasonPicker(collection.divisions, shown, onSelectSeason) }
    }
    val rows = rowsOf(listOf(shown))
    val positions = watch.progress.associateBy { it.setId }
    val watchedIds = watch.watched.mapTo(HashSet()) { it.setId }
    items(rows, positions, watchedIds, heldIds, onOpenTitle)
}

@Composable
private fun SeasonPicker(
    divisions: List<Division>,
    shown: Division,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(horizontal = Spacing.large, vertical = Spacing.small)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { expanded = true }
                    .semantics { contentDescription = "Season: ${shown.title}" }
                    .padding(vertical = Spacing.small),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${shown.title} · ${shown.items.size} ${if (shown.items.size == 1) "episode" else "episodes"}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(text = "▾", style = MaterialTheme.typography.titleMedium)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (division in divisions) {
                DropdownMenuItem(
                    text = { Text("${division.title} · ${division.items.size} ${if (division.items.size == 1) "episode" else "episodes"}") },
                    onClick = {
                        expanded = false
                        onSelect(division.title)
                    },
                )
            }
        }
    }
}

/**
 * One season's episodes, in the same rows [CollectionScreen] draws for a
 * course — a season is just the one division a search result or another
 * direct link may still open on its own, the same way it always has.
 */
@Composable
internal fun SeasonScreen(division: Division, watch: WatchSnapshot, heldIds: Set<String>, onOpenTitle: (setId: String) -> Unit) {
    val rows = remember(division) { rowsOf(listOf(division)) }
    val positions = remember(watch) { watch.progress.associateBy { it.setId } }
    val watchedIds = remember(watch) { watch.watched.mapTo(HashSet()) { it.setId } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        items(rows, positions, watchedIds, heldIds, onOpenTitle)
    }
}

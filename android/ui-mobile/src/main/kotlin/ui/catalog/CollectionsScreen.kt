package ui.catalog

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import catalog.Franchise
import catalog.collectionsLineOf
import catalog.spelledCountOf
import designsystem.Spacing
import model.ListOfSets
import model.MediaSet
import ui.common.catalog.DESTINATION_ASPECT

/**
 * Collections as destinations — a Compose port of
 * `collections-page.js#renderCollectionsPage`: the film franchises the
 * library holds, then the viewer's own lists, each a large card wrapping
 * across the width — one [LazyColumn] end to end, not the hero over a
 * nested, second scrollable: a phone screen short enough to clip a nested
 * section once left "＋ New list" beyond a list that never scrolled. A
 * single scroll also gives this hero a real list position to bleed the bar
 * over, the same way Movies' or Series' own hero does. Each line of cards is
 * an item of that list ([tileLines]), so only the lines in view are built.
 *
 * [setsById] resolves a list's titles for its card's art ([listsSection]).
 */
@Composable
internal fun CollectionsScreen(
    franchises: List<Franchise>,
    lists: List<ListOfSets>,
    setsById: Map<String, MediaSet>,
    onOpenFranchise: (Long) -> Unit,
    onOpenList: (id: String) -> Unit,
    onCreateList: (name: String) -> Unit,
    onOpenTitle: (String) -> Unit,
    state: LazyListState = rememberLazyListState(),
) {
    var naming by remember { mutableStateOf(false) }
    // The same lead a franchise's own page would pick — the web links this
    // hero's quote to that film's own page (`collections-page.js:74`), not
    // to a franchise or a list.
    val lead: MediaSet? = franchises.firstOrNull()?.films?.find { it.backdropPath != null }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = tileColumnsOf(maxWidth - Spacing.medium * 2, DESTINATION_MIN_WIDTH)
        LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Spacing.large)) {
            item(key = "hero") {
                DepartmentHero(
                    title = "Collections",
                    line = collectionsLineOf(franchises.size, lists.size),
                    lead = lead,
                    onOpenTitle = onOpenTitle,
                )
            }
            if (franchises.isNotEmpty()) {
                item(key = "franchises-heading") { DeptRowHeading(title = "Franchises") }
                tileLines("franchises", franchises, columns, Franchise::id, Modifier.padding(horizontal = Spacing.medium)) { franchise, modifier ->
                    ArtTile(
                        name = franchise.name,
                        meta = spelledCountOf(franchise.films.size, "film"),
                        art = franchise.art,
                        aspectRatio = DESTINATION_ASPECT,
                        onClick = { onOpenFranchise(franchise.id) },
                        modifier = modifier,
                        destination = true,
                    )
                }
            }
            item(key = "lists-heading") { DeptRowHeading(title = "Your lists") }
            listsSection(lists = lists, setsById = setsById, columns = columns, onOpen = onOpenList, onNewList = { naming = true })
        }
    }

    if (naming) {
        ListNameDialog(
            title = "Name for the list",
            confirmLabel = "Create",
            onConfirm = { name ->
                naming = false
                onCreateList(name)
            },
            onDismiss = { naming = false },
        )
    }
}

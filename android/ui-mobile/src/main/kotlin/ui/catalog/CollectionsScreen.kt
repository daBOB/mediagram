package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import catalog.Franchise
import catalog.collectionsLineOf
import designsystem.Spacing
import model.ListOfSets
import model.MediaSet

private val DEPT_CARD_WIDTH = 140.dp

/**
 * Collections as destinations — a Compose port of
 * `collections-page.js#renderCollectionsPage`: the film franchises the
 * library holds, each a large card, then the viewer's own lists — one
 * [LazyColumn] end to end, not the hero and franchise row over a nested,
 * second scrollable: a phone screen short enough to clip the franchise row
 * already left "＋ New list" beyond a list that never scrolled either. A
 * single scroll also gives this hero a real list position to bleed the bar
 * over, the same way Movies' or Series' own hero does.
 *
 * A list's own rows are [listsSection], reused as-is: it already draws each
 * list with its title count and the same "＋ New list" action the web page
 * offers, and is the one part of this department that already existed and
 * stayed tested going in — only the franchises row above it is new.
 */
@Composable
internal fun CollectionsScreen(
    franchises: List<Franchise>,
    lists: List<ListOfSets>,
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

    LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Spacing.large)) {
        item {
            DepartmentHero(
                kicker = "Only in your library",
                title = "Collections",
                line = collectionsLineOf(franchises.size, lists.size),
                lead = lead,
                onOpenTitle = onOpenTitle,
            )
        }
        if (franchises.isNotEmpty()) {
            item { DeptRowHeading(title = "Franchises") }
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                    contentPadding = PaddingValues(horizontal = Spacing.medium),
                ) {
                    items(items = franchises, key = Franchise::id) { franchise ->
                        PosterCard(
                            posterPath = franchise.art,
                            title = franchise.name,
                            caption = countOf(franchise.films.size, "film"),
                            modifier = Modifier.width(DEPT_CARD_WIDTH),
                            onClick = { onOpenFranchise(franchise.id) },
                        )
                    }
                }
            }
        }
        item { DeptRowHeading(title = "Your lists") }
        listsSection(lists = lists, onOpen = onOpenList, onNewList = { naming = true })
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

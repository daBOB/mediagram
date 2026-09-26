package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import catalog.Franchise
import designsystem.Spacing
import model.ListOfSets
import model.MediaSet

private val DEPT_CARD_WIDTH = 140.dp

/**
 * Collections as destinations — a Compose port of
 * `collections-page.js#renderCollectionsPage`: the film franchises the
 * library holds, each a large card, then the viewer's own lists.
 *
 * A list's own card grid is [ListsScreen], reused as-is: it already draws
 * each list with its title count and the same "＋ New list" action the web
 * page offers, and is the one part of this department that already existed
 * and stayed tested going in — only the franchises row above it is new.
 */
@Composable
internal fun CollectionsScreen(
    franchises: List<Franchise>,
    lists: List<ListOfSets>,
    onOpenFranchise: (Long) -> Unit,
    onOpenList: (id: String) -> Unit,
    onCreateList: (name: String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        DepartmentHero(
            kicker = "Only in your library",
            title = "Collections",
            line = collectionsLine(franchises.size, lists.size),
            lead = franchises.firstOrNull()?.films?.find { it.backdropPath != null },
            onOpenTitle = {},
        )
        if (franchises.isNotEmpty()) {
            DeptRowHeading(title = "Franchises")
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
        DeptRowHeading(title = "Your lists")
        ListsScreen(lists = lists, onOpen = onOpenList, onCreate = onCreateList)
    }
}

/** "N franchises · M lists" — the franchise count dropped when there are none, matching the web's own line. */
private fun collectionsLine(franchiseCount: Int, listCount: Int): String =
    listOfNotNull(
        franchiseCount.takeIf { it > 0 }?.let { countOf(it, "franchise") },
        countOf(listCount, "list"),
    ).joinToString(" · ")

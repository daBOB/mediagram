package ui.tv.catalog

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import catalog.Franchise
import designsystem.Overscan
import designsystem.Spacing
import java.io.File
import model.ListOfSets

/** How wide a franchise card is on the Collections page's own row. */
private val FranchiseTileWidth = 140.dp

/**
 * The Collections department — the television twin of the web's
 * `collections-page.js`: the franchises the library holds, largest first,
 * each a card into [TvFranchisePage], then the household's own hand-built
 * lists as [TvLists] already draws them. Collections moved out of the three
 * kept masthead tabs into the department row for this phase (per
 * `mastheadSplitOf`); the lists it used to show alone now share the page
 * with franchises, the same split the web's own Collections tab makes.
 */
@Composable
internal fun TvCollectionsPage(
    franchises: List<Franchise>,
    lists: List<ListOfSets>,
    onOpenFranchise: (id: Long) -> Unit,
    onOpenList: (id: String) -> Unit,
    onCreateList: (name: String) -> Unit,
    restoreKey: String? = null,
) {
    // Not one scrollable column: `TvLists` is its own `LazyColumn` with its
    // own arrival-focus scrolling, and nesting two vertically scrollable
    // layouts inside each other is what Compose refuses to measure. The
    // franchise row is fixed-height content above it instead, and `TvLists`
    // fills whatever is left ([Modifier.weight]) with its own scroll intact.
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical)) {
        if (franchises.isNotEmpty()) {
            TvCountedHeading("Franchises", franchises.size)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = Spacing.small, bottom = Spacing.large),
                horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            ) {
                for (franchise in franchises) {
                    TvPlate(
                        title = franchise.name,
                        posterPath = franchise.art?.let(::File),
                        onOpen = { onOpenFranchise(franchise.id) },
                        modifier = Modifier.width(FranchiseTileWidth),
                        caption = "${franchise.films.size} films",
                    )
                }
            }
        }
        TvSectionHeading("Your lists")
        Column(modifier = Modifier.weight(1f)) {
            TvLists(lists = lists, onOpen = onOpenList, onCreate = onCreateList, restoreKey = restoreKey)
        }
    }
}

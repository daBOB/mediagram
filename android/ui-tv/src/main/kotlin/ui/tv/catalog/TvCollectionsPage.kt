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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
 * lists as [TvLists] already draws them. Collections shares this one page
 * with franchises, the same split the web's own Collections tab makes,
 * rather than the three kept masthead tabs it used to sit among.
 *
 * [restoreKey] finds its plate on whichever half it names: a franchise's id
 * takes the remote back to that card, and [TvLists]' own arrival focus is
 * suppressed while it does — a franchise reopened from a list scrolled past
 * would otherwise lose the remote to whichever list row [TvLists] itself
 * would have focused first. With no match, or a list's own id, [TvLists]
 * takes it as it always has.
 *
 * The page's own overscan inset is only applied at the top: [TvLists] is
 * already inset by its own `contentPadding`, so padding this page's bottom
 * too would double it away from the one thing here tall enough to be
 * squeezed by it.
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
    val franchiseFocus = remember { FocusRequester() }
    val franchiseIndex =
        remember(franchises, restoreKey) {
            restoreKey?.let { wanted -> franchises.indexOfFirst { it.id.toString() == wanted }.takeIf { it >= 0 } }
        }
    val takesFocus = LocalTakesArrivalFocus.current
    LaunchedEffect(franchiseIndex, restoreKey) {
        if (franchiseIndex != null && takesFocus) franchiseFocus.requestFocus()
    }

    // Not one scrollable column: `TvLists` is its own `LazyColumn` with its
    // own arrival-focus scrolling, and nesting two vertically scrollable
    // layouts inside each other is what Compose refuses to measure. The
    // franchise row is fixed-height content above it instead, and `TvLists`
    // fills whatever is left (`Modifier.weight`) with its own scroll intact.
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Overscan.horizontal).padding(top = Overscan.vertical)) {
        if (franchises.isNotEmpty()) {
            TvCountedHeading("Franchises", franchises.size)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = Spacing.small, bottom = Spacing.large),
                horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            ) {
                franchises.forEachIndexed { index, franchise ->
                    TvPlate(
                        title = franchise.name,
                        posterPath = franchise.art?.let(::File),
                        onOpen = { onOpenFranchise(franchise.id) },
                        modifier =
                            Modifier.width(FranchiseTileWidth).let {
                                if (index == franchiseIndex) it.focusRequester(franchiseFocus) else it
                            },
                        caption = "${franchise.films.size} films",
                    )
                }
            }
        }
        TvSectionHeading("Your lists")
        CompositionLocalProvider(LocalTakesArrivalFocus provides (takesFocus && franchiseIndex == null)) {
            Column(modifier = Modifier.weight(1f)) {
                TvLists(lists = lists, onOpen = onOpenList, onCreate = onCreateList, restoreKey = restoreKey)
            }
        }
    }
}

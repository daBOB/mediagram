package ui.tv.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.CatalogUiState
import catalog.KeptKind
import catalog.Shelf
import catalog.catalogTabsOf
import catalog.continueWall
import catalog.homeRowsOf
import catalog.kidsShelf
import catalog.watchlistWall
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvTypeScale
import model.WatchSnapshot
import ui.tv.TvSafeArea
import ui.tv.profile.TvChosenProfile

/**
 * The catalogue on a television: [TvMasthead] across the top and, below
 * it, whichever entry is selected — Home, a shelf's wall, or one of the
 * four kept entries. The television twin of the phone's `CatalogScreen`,
 * with the same callbacks out, so whatever owns the positions around it
 * drives both surfaces the same way.
 *
 * The masthead stays even while there are no shelves to show — loading,
 * an empty library, a kids profile with nothing rated for it yet, a failed
 * read — carrying only the viewer's name then. On the phone the bar's
 * profile button is that way out; here the masthead is the bar, and a kids
 * profile left on a message with nothing to press would be a dead end.
 *
 * [mastheadFocus] is the handle a caller uses to send the remote back up to
 * the masthead, which is where Back goes first from the catalogue's root.
 */
@Composable
fun TvCatalogScreen(
    state: CatalogUiState,
    profile: TvChosenProfile,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onOpenList: (id: String) -> Unit,
    onCreateList: (name: String) -> Unit,
    mastheadFocus: FocusRequester = remember { FocusRequester() },
) {
    val ready = (state as? CatalogUiState.Ready)?.takeIf { it.shelves.isNotEmpty() }
    val shelves = ready?.shelves.orEmpty()
    // Ordering, index-to-tab mapping and the labels themselves are
    // catalogTabsOf's, the same function the phone's masthead reads.
    val tabs = remember(shelves) { catalogTabsOf(shelves) }
    var chosen by rememberSaveable { mutableIntStateOf(0) }
    // A refresh can return a library with fewer shelves than the one that
    // was on screen when it started.
    val selected = chosen.coerceIn(0, tabs.titles.lastIndex)

    // With no wall below to take focus, the masthead is the one thing on
    // screen the remote can rest on.
    LaunchedEffect(ready == null) {
        if (ready == null) mastheadFocus.requestFocus()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TvMasthead(
            titles = if (ready != null) tabs.titles else emptyList(),
            selected = selected,
            firstKeptIndex = tabs.firstKept,
            profile = profile,
            onSelect = { chosen = it },
            focusRequester = mastheadFocus,
        )
        // Above the shelf, not instead of it: the library below is the one
        // that was on this device before the refresh was tried, and it is
        // still every bit of it.
        ready?.notice?.let { notice ->
            Text(
                text = notice,
                style = TvTypeScale.body,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Overscan.horizontal, vertical = Spacing.small),
            )
        }
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state is CatalogUiState.Loading -> TvCenteredMessage("Loading your library…")
                state is CatalogUiState.KidsEmpty -> TvCenteredMessage("Nothing rated FSK 12 or under yet.")
                state is CatalogUiState.Failed -> TvCenteredMessage(state.message)
                ready == null -> TvCenteredMessage("The library is empty.")
                selected == 0 -> {
                    TvHome(
                        rows = remember(shelves, ready.watch) { homeRowsOf(shelves, ready.watch) },
                        watch = ready.watch,
                        onOpenTitle = onOpenTitle,
                        onOpenCollection = onOpenCollection,
                        onSeeAll = { shelf -> chosen = tabs.titles.indexOf(shelf).coerceAtLeast(0) },
                    )
                }
                selected < tabs.firstKept -> {
                    TvShelfWall(shelves[selected - 1], ready.watch, onOpenTitle, onOpenCollection)
                }
                else -> {
                    TvKeptTab(
                        kind = KeptKind.entries[selected - tabs.firstKept],
                        shelves = shelves,
                        watch = ready.watch,
                        onOpenTitle = onOpenTitle,
                        onOpenCollection = onOpenCollection,
                        onOpenList = onOpenList,
                        onCreateList = onCreateList,
                    )
                }
            }
        }
    }
}

/**
 * Which of the masthead's four kept entries is selected, dispatched to what
 * draws it — the phone's `KeptTabContent`, over the same four functions.
 */
@Composable
private fun TvKeptTab(
    kind: KeptKind,
    shelves: List<Shelf>,
    watch: WatchSnapshot,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onOpenList: (id: String) -> Unit,
    onCreateList: (name: String) -> Unit,
) {
    when (kind) {
        KeptKind.CONTINUE -> TvKeptWall(kind, remember(shelves, watch) { continueWall(shelves, watch) }, watch, onOpenTitle)
        KeptKind.WATCHLIST -> TvKeptWall(kind, remember(shelves, watch) { watchlistWall(shelves, watch) }, watch, onOpenTitle)
        KeptKind.KIDS -> TvKidsWall(remember(shelves, watch) { kidsShelf(shelves, watch) }, watch, onOpenTitle, onOpenCollection)
        KeptKind.COLLECTIONS -> TvLists(lists = watch.collections, onOpen = onOpenList, onCreate = onCreateList)
    }
}

/**
 * What a screen says when it has nothing to show, in the phone's exact
 * words — a viewer who uses both surfaces reads the same sentence on each.
 */
@Composable
internal fun TvCenteredMessage(message: String) {
    TvSafeArea {
        Text(
            text = message,
            style = TvTypeScale.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

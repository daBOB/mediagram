package ui.tv.catalog

import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import catalog.Franchise
import catalog.KeptKind
import catalog.collectionsLineOf
import catalog.listArtOf
import catalog.spelledCountOf
import designsystem.Spacing
import kotlinx.coroutines.flow.first
import model.ListOfSets
import model.MediaSet
import ui.common.catalog.DESTINATION_ASPECT
import ui.tv.catalog.home.TvBandHeading
import ui.tv.chrome.LocalTvPagePadding
import ui.tv.rememberStableRequester

/**
 * Three cards across — the web's `.destinations` (`repeat(auto-fill,
 * minmax(16rem, 1fr))`, `departments.css`) worked out once for the width
 * this page has beside the rail on television's one fixed 960dp. Search's
 * Collections part takes the same three: its full-width pushed frame
 * (864dp inside the overscan) still fits three 16rem tracks and not four.
 */
internal const val DestinationColumns = 3

/** `.destinations{gap:16px}`. */
private val DestinationGap = 16.dp

/** Lets a test scroll this page's own list directly, the same reason [TvMoviesDepartmentPageTestTag] exists. */
internal const val TvCollectionsPageTestTag = "tv-collections-page"

/**
 * The Collections department — the television twin of the web's
 * `collections-page.js#renderCollectionsPage`: [TvDepartmentHero], the
 * franchises the library holds largest first, then the household's own
 * lists, both as the web's large 4:3 cards ([TvArtTile]) wrapping three to a
 * line — a list pictured by its first pictured title ([listArtOf], via
 * [setsById]) — and "＋ New list" as a round pill under them.
 *
 * One `LazyColumn` end to end, each line of cards its own item: a hero tall
 * enough to need the room a nested scroll can't measure (Compose refuses
 * two vertical scrollables, one inside the other) once left a section below
 * the fold with nothing able to scroll it into view, and a library's worth
 * of franchises composed all at once is more than a weak box draws in one
 * frame.
 *
 * Arrival lands on the card [restoreKey] names — a franchise's id or a
 * list's — else the first franchise, else the first list, else "＋ New
 * list"; a card on its section's first line brings that section's heading
 * into view with it.
 *
 * With no lists at all, a line says so above the pill, where the web leaves
 * an empty grid — the phone's own reason: an empty band under a heading
 * reads as something that failed to load.
 */
@Composable
internal fun TvCollectionsPage(
    franchises: List<Franchise>,
    lists: List<ListOfSets>,
    setsById: Map<String, MediaSet>,
    onOpenFranchise: (id: Long) -> Unit,
    onOpenList: (id: String) -> Unit,
    onCreateList: (name: String) -> Unit,
    restoreKey: String? = null,
    listState: LazyListState = rememberLazyListState(),
) {
    // Saveable, so a rotation or a process death mid-name comes back to the
    // question rather than to the page under it.
    var naming by rememberSaveable { mutableStateOf(false) }
    if (naming) {
        TvListNameQuestion(
            onConfirm = { name ->
                naming = false
                onCreateList(name)
            },
            onDismiss = { naming = false },
        )
        return
    }

    // The same lead a franchise's own page would pick — the tablet's own
    // hero links its quote to this film's page too (`collections-page.js:74`),
    // not to a franchise or a list; television never links its hero at all.
    val lead = remember(franchises) { franchises.firstOrNull()?.films?.find { it.backdropPath != null } }
    val focus = remember { FocusRequester() }
    val franchiseRows = remember(franchises) { franchises.chunked(DestinationColumns) }
    val listRows = remember(lists) { lists.chunked(DestinationColumns) }
    val target = remember(franchises, lists, restoreKey) { arrivalOf(franchises, lists, restoreKey) }

    val takesFocus = LocalTakesArrivalFocus.current
    val density = LocalDensity.current
    LaunchedEffect(target, takesFocus) {
        if (!takesFocus) return@LaunchedEffect
        val items = itemKeysOf(franchiseRows.size, listRows.size)
        val itemIndex = items.indexOf(target.row)
        // `scrollToItem` alone lands the item flush against this list's own
        // top, behind the bar — it never reads [LocalBringIntoViewSpec] — so
        // a `scrollBy` right after it backs off by the bar's own clearance.
        listState.scrollToItem(items.indexOf(target.scrollTo))
        listState.scrollBy(-with(density) { TvBarClearance.toPx() })
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }.first { info -> info.any { it.index == itemIndex } }
        focus.requestFocus()
    }

    val pagePadding = LocalTvPagePadding.current
    // [TvWall]'s own doc on why this wraps the whole scrollable rather than
    // each item inside it: a heading Down scrolled to the top landed half
    // behind the bar the same way a plate once did.
    val barClearance = rememberTvBarClearanceBringIntoView()
    CompositionLocalProvider(LocalBringIntoViewSpec provides barClearance) {
        LazyColumn(
            state = listState,
            // Scoped to this list alone — [TvMoviesDepartmentPage]'s own doc on
            // why this coexists with the explicit request above.
            modifier = Modifier.fillMaxSize().testTag(TvCollectionsPageTestTag).toTopWhenLeftForTheBar { listState.animateScrollToItem(0) }.focusRestorer(fallback = focus),
            contentPadding = PaddingValues(start = pagePadding.start, top = pagePadding.top, end = pagePadding.end, bottom = pagePadding.bottom),
        ) {
            item(key = HERO) {
                TvDepartmentHero(title = "Collections", line = collectionsLineOf(franchises.size, lists.size), lead = lead)
            }
            if (franchises.isNotEmpty()) {
                item(key = FRANCHISES_HEADING) { SectionHeading("Franchises") }
                destinationRows(FRANCHISES, franchiseRows, Franchise::id, focus, target) { franchise, modifier ->
                    TvArtTile(
                        name = franchise.name,
                        meta = spelledCountOf(franchise.films.size, "film"),
                        art = franchise.art,
                        aspectRatio = DESTINATION_ASPECT,
                        onOpen = { onOpenFranchise(franchise.id) },
                        modifier = modifier,
                        destination = true,
                    )
                }
            }
            item(key = LISTS_HEADING) { SectionHeading("Your lists") }
            if (lists.isEmpty()) {
                item(key = EMPTY) { TvQuietLine(KeptKind.COLLECTIONS.empty, Modifier.padding(top = Spacing.small)) }
            } else {
                destinationRows(LISTS, listRows, ListOfSets::id, focus, target) { list, modifier ->
                    TvArtTile(
                        name = list.name,
                        meta = spelledCountOf(list.items.size, "title"),
                        art = listArtOf(list, setsById),
                        aspectRatio = DESTINATION_ASPECT,
                        onOpen = { onOpenList(list.id) },
                        modifier = modifier,
                        destination = true,
                    )
                }
            }
            item(key = NEW) {
                Box(modifier = Modifier.padding(top = 20.dp)) {
                    TvPagePill(text = "＋ New list", onClick = { naming = true }, modifier = Modifier.focusRequester(rememberStableRequester(focus.takeIf { target.item == NEW })))
                }
            }
        }
    }
}

/**
 * One section's cards, a line of [DestinationColumns] per item; a short last
 * line keeps the others' widths, as the web's `auto-fill` tracks do.
 *
 * [card]'s modifier carries [focus] on the one card [arrival] names and
 * [rememberStableRequester]'s own requester on every other, so a card's
 * modifier chain is the same shape whichever card the target moves to.
 */
private fun <T> LazyListScope.destinationRows(
    section: String,
    rows: List<List<T>>,
    id: (T) -> Any,
    focus: FocusRequester,
    arrival: Arrival,
    card: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    rows.forEachIndexed { index, row ->
        item(key = "$section-row:$index") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = if (index == 0) Spacing.small else DestinationGap),
                horizontalArrangement = Arrangement.spacedBy(DestinationGap),
            ) {
                // Keyed by the card's own identity, not its slot, so a
                // reorder never leaves focus on whichever card moved into it.
                for (item in row) {
                    key(id(item)) {
                        card(item, Modifier.weight(1f).focusRequester(rememberStableRequester(focus.takeIf { arrival.item == "$section:${id(item)}" })))
                    }
                }
                repeat(DestinationColumns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String) {
    Column(modifier = Modifier.padding(top = Spacing.large)) { TvBandHeading(title = title, count = null) }
}

/**
 * Where arrival lands: [item] names the card (or the pill), [row] the list
 * item holding it, and [scrollTo] the item brought to the top to show it —
 * the section's heading while the card is on its section's first line.
 */
private data class Arrival(val item: String, val row: String, val scrollTo: String)

private fun arrivalOf(
    franchises: List<Franchise>,
    lists: List<ListOfSets>,
    restoreKey: String?,
): Arrival {
    fun at(
        section: String,
        heading: String,
        id: Any,
        index: Int,
    ): Arrival {
        val row = "$section-row:${index / DestinationColumns}"
        return Arrival("$section:$id", row, if (index < DestinationColumns) heading else row)
    }
    franchises.indexOfFirst { it.id.toString() == restoreKey }.takeIf { it >= 0 }?.let { return at(FRANCHISES, FRANCHISES_HEADING, franchises[it].id, it) }
    lists.indexOfFirst { it.id == restoreKey }.takeIf { it >= 0 }?.let { return at(LISTS, LISTS_HEADING, lists[it].id, it) }
    return when {
        franchises.isNotEmpty() -> at(FRANCHISES, FRANCHISES_HEADING, franchises[0].id, 0)
        lists.isNotEmpty() -> at(LISTS, LISTS_HEADING, lists[0].id, 0)
        else -> Arrival(NEW, NEW, LISTS_HEADING)
    }
}

/** The page's own item keys in order — what [arrivalOf]'s names resolve to as list indices. */
private fun itemKeysOf(
    franchiseRows: Int,
    listRows: Int,
): List<String> =
    buildList {
        add(HERO)
        if (franchiseRows > 0) {
            add(FRANCHISES_HEADING)
            repeat(franchiseRows) { add("$FRANCHISES-row:$it") }
        }
        add(LISTS_HEADING)
        if (listRows == 0) add(EMPTY) else repeat(listRows) { add("$LISTS-row:$it") }
        add(NEW)
    }

private const val HERO = "hero"
private const val FRANCHISES = "franchise"
private const val FRANCHISES_HEADING = "franchises-heading"
private const val LISTS = "list"
private const val LISTS_HEADING = "lists-heading"
private const val EMPTY = "empty"
private const val NEW = "new"

package ui.tv.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import catalog.Franchise
import catalog.KeptKind
import catalog.collectionsLineOf
import designsystem.Spacing
import java.io.File
import kotlinx.coroutines.flow.first
import model.ListOfSets
import ui.tv.TvTextRow
import ui.tv.chrome.LocalTvPagePadding

/** How wide a franchise card is on the Collections page's own row. */
private val FranchiseTileWidth = 140.dp

/** Lets a test scroll this page's own list directly, the same reason [TvMoviesDepartmentPageTestTag] exists. */
internal const val TvCollectionsPageTestTag = "tv-collections-page"

/**
 * The Collections department — the television twin of the web's
 * `collections-page.js`: [TvDepartmentHero], the franchises the library
 * holds largest first, each a card into [TvFranchisePage], then the
 * household's own hand-built lists — [TvListRow], the row [TvLists] itself
 * also draws for the two kept tabs.
 *
 * One `LazyColumn` end to end, not the hero and franchise row over
 * [TvLists]' own separate scrollable: a hero tall enough to need the room a
 * nested scroll can't measure (Compose refuses two vertical scrollables,
 * one inside the other) left the franchise row itself below the fold with
 * nothing able to scroll it into view. Every list is this same list's own
 * item now, the same restructuring the tablet's own `CollectionsScreen`
 * already made for the same reason.
 *
 * [restoreKey] finds its plate on whichever half it names: a franchise's id
 * takes the remote back to that card (scrolling its own row, [scrollThenFocus]'s
 * rule); a list's id, or nothing at all, takes it to a list row instead —
 * the first franchise only when there is no restore key and no list claims
 * it, [DepartmentOrShelfWall]'s own default-arrival reading of this page.
 */
@Composable
internal fun TvCollectionsPage(
    franchises: List<Franchise>,
    lists: List<ListOfSets>,
    onOpenFranchise: (id: Long) -> Unit,
    onOpenList: (id: String) -> Unit,
    onCreateList: (name: String) -> Unit,
    restoreKey: String? = null,
    listState: LazyListState = rememberLazyListState(),
) {
    // Saveable like `TvLists`' own flag: a rotation or a process death
    // mid-name comes back to the question rather than to the page under it.
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
    val franchiseFocus = remember { FocusRequester() }
    val listFocus = remember { FocusRequester() }
    val franchiseRowState = rememberLazyListState()

    // A franchise named by [restoreKey] takes the remote back to its card;
    // with nothing named (or a key no list carries either) the first
    // franchise does, the page's own default arrival stop.
    val franchiseIndex =
        remember(franchises, lists, restoreKey) {
            restoreKey?.let { wanted -> franchises.indexOfFirst { it.id.toString() == wanted }.takeIf { it >= 0 } }
                ?: 0.takeIf { franchises.isNotEmpty() && lists.none { it.id == restoreKey } }
        }
    val listIsNew = lists.isEmpty()
    val listIndex = remember(lists, restoreKey) { lists.indexOfFirst { it.id == restoreKey }.coerceAtLeast(0) }

    val included =
        remember(franchises, lists) {
            buildList {
                add("hero")
                if (franchises.isNotEmpty()) add("franchises")
                add("lists-heading")
                if (lists.isEmpty()) add("new") else { addAll(lists.indices.map { "list:$it" }); add("new") }
            }
        }
    val targetKey = if (franchiseIndex != null) "franchises" else if (listIsNew) "new" else "list:$listIndex"
    val takesFocus = LocalTakesArrivalFocus.current
    var sectionInView by remember { mutableStateOf(false) }
    LaunchedEffect(targetKey, takesFocus) {
        sectionInView = false
        if (!takesFocus) return@LaunchedEffect
        val itemIndex = included.indexOf(targetKey)
        listState.scrollToItem(itemIndex)
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }.first { info -> info.any { it.index == itemIndex } }
        sectionInView = true
    }
    // The franchise row's own inner scroll, once its own outer item is on
    // screen — [scrollThenFocus]'s rule, the same a department row's own
    // `LaunchedEffect` already applies for exactly this reason.
    LaunchedEffect(sectionInView, franchiseIndex, takesFocus) {
        scrollThenFocus(franchiseRowState, franchiseIndex.takeIf { sectionInView }, franchiseFocus, takesFocus)
    }
    // A list row or "＋ New list" is not nested in a row of its own — once
    // its own outer item is on screen its `focusRequester` is already
    // attached and ready.
    LaunchedEffect(sectionInView, targetKey, takesFocus) {
        if (sectionInView && takesFocus && franchiseIndex == null) listFocus.requestFocus()
    }

    val pagePadding = LocalTvPagePadding.current
    LazyColumn(
        state = listState,
        // Scoped to this list alone — [TvMoviesDepartmentPage]'s own doc on
        // why this coexists with the explicit requests above.
        modifier =
            Modifier.fillMaxSize().testTag(TvCollectionsPageTestTag)
                .focusRestorer(fallback = if (franchiseIndex != null) franchiseFocus else listFocus),
        contentPadding = PaddingValues(top = pagePadding.top, bottom = pagePadding.bottom),
    ) {
        item(key = "hero") {
            Column(modifier = Modifier.padding(start = pagePadding.start, end = pagePadding.end)) {
                TvDepartmentHero(title = "Collections", line = collectionsLineOf(franchises.size, lists.size), lead = lead)
            }
        }
        if (franchises.isNotEmpty()) {
            item(key = "franchises") {
                Column(modifier = Modifier.padding(start = pagePadding.start, end = pagePadding.end)) {
                    TvCountedHeading("Franchises", franchises.size)
                    LazyRow(
                        state = franchiseRowState,
                        modifier = Modifier.padding(top = Spacing.small),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                    ) {
                        itemsIndexed(franchises, key = { _, franchise -> franchise.id }) { index, franchise ->
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
            }
        }
        item(key = "lists-heading") {
            Box(modifier = Modifier.padding(start = pagePadding.start, end = pagePadding.end, top = Spacing.large)) {
                TvSectionHeading("Your lists")
            }
        }
        if (lists.isEmpty()) {
            item(key = "empty") {
                Box(modifier = Modifier.padding(start = pagePadding.start, end = pagePadding.end, top = Spacing.small)) {
                    TvQuietLine(KeptKind.COLLECTIONS.empty)
                }
            }
        } else {
            itemsIndexed(lists, key = { _, list -> list.id }) { index, list ->
                Box(modifier = Modifier.padding(start = pagePadding.start, end = pagePadding.end, top = Spacing.small)) {
                    TvListRow(list, onOpen = onOpenList, focusRequester = listFocus.takeIf { index == listIndex && franchiseIndex == null })
                }
            }
        }
        item(key = "new") {
            Box(modifier = Modifier.padding(start = pagePadding.start, end = pagePadding.end, top = Spacing.small)) {
                TvTextRow(
                    text = "＋ New list",
                    onClick = { naming = true },
                    focusRequester = listFocus.takeIf { listIsNew && franchiseIndex == null },
                )
            }
        }
    }
}

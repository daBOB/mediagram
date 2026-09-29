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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import catalog.CatalogUiState
import catalog.DocumentariesDepartment
import catalog.DocumentaryLibrary
import catalog.Entry
import catalog.Shelf
import catalog.allSetsById
import catalog.documentariesDepartmentOf
import catalog.documentariesLineOf
import catalog.factsLine
import catalog.resumeCardsOf
import designsystem.Spacing
import model.MediaSet
import model.WatchSnapshot

private val DEPT_CARD_WIDTH = 140.dp

/** For a test to scroll a short window to a row further down the page — this page runs to more rows than a phone's own height shows at once. */
internal const val DOCUMENTARIES_DEPT_TEST_TAG = "documentaries-department"

/**
 * The Documentaries shelf, as its own tab. Unlike Movies, Series and
 * Tutorials — omitted from the shelf list entirely while they hold nothing,
 * so their own department builder never runs on an empty one — Documentaries
 * is never omitted (its pill still reads "0"), so this is the one department
 * whose own empty state actually has to be drawn rather than never reached.
 */
@Composable
internal fun DocumentariesDepartment(
    shelf: Shelf,
    state: CatalogUiState.Ready,
    onOpenCollection: (String) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    onPlay: (String) -> Unit,
) {
    val byId = remember(state.shelves) { allSetsById(state.shelves) }
    val library = remember(shelf) {
        DocumentaryLibrary(
            collections = shelf.entries.filterIsInstance<Entry.Collection>(),
            singles = shelf.entries.filterIsInstance<Entry.Film>().map { it.set },
        )
    }
    val department = remember(library, byId, state.watch) { documentariesDepartmentOf(library, byId, state.watch) }
    department?.let {
        DocumentariesDepartmentScreen(
            department = it,
            watch = state.watch,
            heldIds = state.heldIds,
            onOpenCollection = onOpenCollection,
            onPlay = onPlay,
            state = listState,
        )
    } ?: CenteredMessage("No documentaries yet. Upload one with mediagram add-docu <file|folder>.")
}

/**
 * The Documentaries department's opening page — a Compose port of
 * `department-pages.js#renderDocumentariesDept`: a hero, what is underway,
 * one row per hand-set category, what arrived, one row per folder, then
 * whatever was uploaded on its own.
 *
 * Unlike Movies, a plate here plays on tap rather than opening a title page
 * — the web's own `renderDocumentariesDept` wires every row to `cx.play`,
 * not `cx.openFilm`: nothing about a documentary comes from a provider, so
 * there is no synopsis or cast worth a stop before playing it. The hero
 * itself is not a link either way, the same as the web's own `leadHref: null`.
 */
@Composable
internal fun DocumentariesDepartmentScreen(
    department: DocumentariesDepartment,
    watch: WatchSnapshot,
    heldIds: Set<String>,
    onOpenCollection: (String) -> Unit,
    onPlay: (String) -> Unit,
    state: LazyListState = rememberLazyListState(),
) {
    val positions = remember(watch) { watch.progress.associateBy { it.setId } }
    val watchedIds = remember(watch) { watch.watched.mapTo(HashSet()) { it.setId } }
    val resumeCards = remember(department.continuing, watch, heldIds) {
        resumeCardsOf(department.continuing, emptyList(), watch, heldIds)
    }

    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().testTag(DOCUMENTARIES_DEPT_TEST_TAG),
        contentPadding = PaddingValues(bottom = Spacing.large),
    ) {
        item {
            DepartmentHero(
                kicker = "Only in your library",
                title = "Documentaries",
                line = documentariesLineOf(department),
                lead = department.lead,
                // The web never links a documentaries hero anywhere
                // (`leadHref: null`, `department-pages.js`) — nothing here
                // comes from a provider, so there is no film page for the
                // quote's credit to open either.
                onOpenTitle = null,
            )
        }
        if (resumeCards.isNotEmpty()) {
            item { DeptRowHeading(title = "Continue watching") }
            item { ResumeStrip(cards = resumeCards, onOpenTitle = onPlay) }
        }
        for (row in department.categories) {
            item(key = "category-heading-${row.title}") { DeptRowHeading(title = row.title) }
            item(key = "category-row-${row.title}") { DocumentaryUnitRow(row.units, watchedIds, onOpenCollection, onPlay) }
        }
        if (department.recentlyAdded.isNotEmpty()) {
            item { DeptRowHeading(title = "Recently added") }
            item { DocumentaryRow(department.recentlyAdded, watchedIds, onPlay) }
        }
        for (group in department.collections) {
            val hasMore = group.collection.count > group.preview.size
            item(key = "group-${group.collection.key}") {
                DeptRowHeading(
                    title = group.collection.name,
                    onSeeAll = { onOpenCollection(group.collection.key) }.takeIf { hasMore },
                    seeAllLabel = "All ${group.collection.count} →",
                )
            }
            item(key = "group-row-${group.collection.key}") { DocumentaryRow(group.preview, watchedIds, onPlay) }
        }
        if (department.singles.isNotEmpty()) {
            item { DeptRowHeading(title = "Standalone documentaries") }
            item { DocumentaryRow(department.singles, watchedIds, onPlay) }
        }
    }
}

@Composable
private fun DocumentaryRow(sets: List<MediaSet>, watchedIds: Set<String>, onPlay: (String) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        contentPadding = PaddingValues(horizontal = Spacing.medium),
    ) {
        items(items = sets, key = MediaSet::setId) { set ->
            PosterCard(
                posterPath = set.posterPath,
                title = set.title,
                caption = factsLine(set.year, set.durationSecs),
                watched = set.setId in watchedIds,
                modifier = Modifier.width(DEPT_CARD_WIDTH),
                onClick = { onPlay(set.setId) },
            )
        }
    }
}

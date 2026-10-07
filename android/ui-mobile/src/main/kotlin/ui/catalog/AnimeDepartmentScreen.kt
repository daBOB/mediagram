package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import catalog.AnimeDepartment
import catalog.AnimeLibrary
import catalog.CatalogUiState
import catalog.Department
import catalog.Entry
import catalog.Shelf
import catalog.allSetsById
import catalog.animeDepartmentOf
import catalog.animeLineOf
import catalog.resumeCardsOf
import designsystem.Spacing
import model.WatchSnapshot

/** For a test to scroll a short window to a row further down the page — the same reason [DOCUMENTARIES_DEPT_TEST_TAG] exists. */
internal const val ANIME_DEPT_TEST_TAG = "anime-department"

/**
 * The Anime shelf, as its own tab — omitted from the shelf list entirely
 * while it holds nothing, so unlike [DocumentariesDepartmentTab] this builder
 * never runs on an empty one.
 */
@Composable
internal fun AnimeDepartmentTab(
    shelf: Shelf,
    state: CatalogUiState.Ready,
    columns: Int,
    onOpenTitle: (String) -> Unit,
    onOpenCollection: (String) -> Unit,
    gridState: LazyGridState = rememberLazyGridState(),
    onPlay: (String) -> Unit,
) {
    val byId = remember(state.shelves) { allSetsById(state.shelves) }
    val library = remember(shelf) {
        AnimeLibrary(
            shows = shelf.entries.filterIsInstance<Entry.Collection>(),
            films = shelf.entries.filterIsInstance<Entry.Film>().map { it.set },
        )
    }
    val department = remember(library, byId, state.watch) { animeDepartmentOf(library, byId, state.watch) }
    department?.let {
        AnimeDepartmentScreen(
            department = it,
            watch = state.watch,
            heldIds = state.heldIds,
            columns = columns,
            onOpenTitle = onOpenTitle,
            onOpenCollection = onOpenCollection,
            onPlay = onPlay,
            state = gridState,
        )
    }
}

/**
 * The Anime department's opening page — a Compose port of
 * `anime-department.js`'s own `renderAnimeDept`: a hero, what is underway,
 * every show, then every film — modelled on [ShowsDepartmentScreen], since
 * this shelf mixes shows and films the way none of Movies/Series/Tutorials
 * does.
 */
@Composable
internal fun AnimeDepartmentScreen(
    department: AnimeDepartment,
    watch: WatchSnapshot,
    heldIds: Set<String>,
    columns: Int,
    onOpenTitle: (String) -> Unit,
    onOpenCollection: (String) -> Unit,
    onPlay: (String) -> Unit,
    state: LazyGridState = rememberLazyGridState(),
) {
    val positions = remember(watch) { watch.progress.associateBy { it.setId } }
    val watchedIds = remember(watch) { watch.watched.mapTo(HashSet()) { it.setId } }
    val resumeCards = remember(department.continuing, department.nextUp, watch, heldIds) {
        resumeCardsOf(department.continuing, department.nextUp, watch, heldIds)
    }

    LazyVerticalGrid(
        columns = GutteredCells(columns, Spacing.medium),
        state = state,
        modifier = Modifier.fillMaxSize().testTag(ANIME_DEPT_TEST_TAG),
        contentPadding = PaddingValues(bottom = Spacing.large),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        item(key = "hero", span = { GridItemSpan(maxLineSpan) }) {
            DepartmentHero(
                title = Department.ANIME.label,
                line = animeLineOf(department),
                lead = department.lead,
                onOpenTitle = onOpenTitle,
            )
        }
        if (resumeCards.isNotEmpty()) {
            item(key = "continue-heading", span = { GridItemSpan(maxLineSpan) }) {
                DeptRowHeading(title = "Continue watching")
            }
            item(key = "continue", span = { GridItemSpan(maxLineSpan) }) {
                ResumeStrip(cards = resumeCards, onOpenTitle = onPlay)
            }
        }
        if (department.shows.isNotEmpty()) {
            item(key = "series-heading", span = { GridItemSpan(maxLineSpan) }) {
                DeptRowHeading(title = "Series")
            }
            itemsIndexed(items = department.shows, key = { _, entry -> "series/${entry.key}" }) { index, entry ->
                Box(Modifier.gutteredCell(index, columns, Spacing.medium)) {
                    EntryCard(entry, positions, watchedIds, onOpenTitle, onOpenCollection, heldIds)
                }
            }
        }
        if (department.films.isNotEmpty()) {
            item(key = "films-heading", span = { GridItemSpan(maxLineSpan) }) {
                DeptRowHeading(title = "Films")
            }
            itemsIndexed(items = department.films, key = { _, set -> "film/${set.setId}" }) { index, set ->
                Box(Modifier.gutteredCell(index, columns, Spacing.medium)) {
                    EntryCard(Entry.Film(set), positions, watchedIds, onOpenTitle, onOpenCollection, heldIds)
                }
            }
        }
    }
}

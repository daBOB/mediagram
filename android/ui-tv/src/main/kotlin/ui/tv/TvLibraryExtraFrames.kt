package ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import catalog.BrowseViewModel
import catalog.CatalogUiState
import catalog.Shelf
import catalog.allTitles
import catalog.franchisePageOf
import catalog.genreIndex
import catalog.personPageOf
import model.MediaSet
import model.WatchSnapshot
import ui.LibraryPositions
import ui.catalog.rememberFranchiseOverviews
import ui.catalog.rememberPersonLookup
import ui.catalog.rememberPortrait
import ui.tv.catalog.TvFranchisePage
import ui.tv.catalog.TvGenresIndex
import ui.tv.catalog.TvLatestPage
import ui.tv.catalog.TvMoviesPage
import ui.tv.catalog.TvPersonPage

/**
 * The person, franchise, Genres, Latest and "All N films" frames — five of
 * [TvLibrary]'s smaller branches, kept apart from its own dispatcher so that
 * file reads as a table of what each [ui.FrameKind] draws, not as five more
 * pages' worth of wiring.
 */
@Composable
internal fun TvPersonFrame(
    at: LibraryPositions,
    catalogState: CatalogUiState,
    personId: Long?,
    watch: WatchSnapshot,
    heldIds: Set<String>,
    shelves: List<Shelf>,
    restore: TvRestoreKeys,
    here: Int,
    browse: BrowseViewModel,
    leave: () -> Unit,
) {
    if (personId == null) {
        LaunchedEffect(Unit) { leave() }
        return
    }
    BackHandler(onBack = leave)
    val lookup = rememberPersonLookup(personId, browse::person)
    val page = remember(lookup.person, shelves) { personPageOf(lookup.person, shelves) }
    val portrait = rememberPortrait(personId, lookup.person?.portraitPath, browse::shouldRequestPortrait, browse::fetchPortrait)
    TvPersonPage(
        page = page,
        // "Still asking" outlives the person fetch itself on a cold restore:
        // `shelves` (needed to build the page from a resolved person) is
        // also empty until the catalogue is Ready, so both must clear before
        // this stops saying "loading" — otherwise a nobody-by-that-id flash
        // would just move from before the fetch to before the catalogue.
        loading = lookup.loading || catalogState !is CatalogUiState.Ready,
        portrait = portrait,
        watch = watch,
        heldIds = heldIds,
        onOpenTitle = { setId ->
            restore.opened(here, setId)
            at.openTitle(setId)
        },
        onOpenCollection = { key ->
            restore.opened(here, key)
            at.openCollection(key)
        },
        restoreKey = restore.of(here),
    )
}

@Composable
internal fun TvFranchiseFrame(
    at: LibraryPositions,
    catalogState: CatalogUiState,
    franchiseId: Long?,
    watch: WatchSnapshot,
    heldIds: Set<String>,
    allFilms: List<MediaSet>,
    restore: TvRestoreKeys,
    here: Int,
    browse: BrowseViewModel,
    leave: () -> Unit,
) {
    if (franchiseId == null) {
        LaunchedEffect(Unit) { leave() }
        return
    }
    val overviews = rememberFranchiseOverviews(browse::franchiseOverviews)
    val page = remember(franchiseId, allFilms, overviews) { franchisePageOf(franchiseId, allFilms, overviews) }
    // Resolved the same way a title or a show is ([TvResolvedBranch]): a
    // cold restore lands here before `allFilms` has anything in it, and that
    // must read as "still loading", not as "no such franchise" — leaving
    // before the catalogue has even answered would drop a perfectly good
    // frame on a restore that just arrived first.
    TvResolvedBranch(page, catalogState, leave) { resolved ->
        TvFranchisePage(
            page = resolved,
            watch = watch,
            onOpenTitle = { setId ->
                restore.opened(here, setId)
                at.openTitle(setId)
            },
            restoreKey = restore.of(here),
            heldIds = heldIds,
        )
    }
}

@Composable
internal fun TvGenresFrame(
    at: LibraryPositions,
    shelves: List<Shelf>,
    restore: TvRestoreKeys,
    here: Int,
    leave: () -> Unit,
) {
    BackHandler(onBack = leave)
    val genres = remember(shelves) { genreIndex(allTitles(shelves)) }
    TvGenresIndex(
        genres = genres,
        onOpenGenre = { name ->
            restore.opened(here, name)
            at.openGenre(name)
        },
        restoreKey = restore.of(here),
    )
}

@Composable
internal fun TvLatestFrame(
    at: LibraryPositions,
    shelves: List<Shelf>,
    watch: WatchSnapshot,
    heldIds: Set<String>,
    restore: TvRestoreKeys,
    here: Int,
    leave: () -> Unit,
) {
    BackHandler(onBack = leave)
    TvLatestPage(
        shelves = shelves,
        watch = watch,
        heldIds = heldIds,
        onOpenTitle = { setId ->
            restore.opened(here, setId)
            at.openTitle(setId)
        },
        onOpenCollection = { key ->
            restore.opened(here, key)
            at.openCollection(key)
        },
        restoreKey = restore.of(here),
    )
}

@Composable
internal fun TvMoviesPageFrame(
    at: LibraryPositions,
    allFilms: List<MediaSet>,
    watch: WatchSnapshot,
    heldIds: Set<String>,
    restore: TvRestoreKeys,
    here: Int,
    leave: () -> Unit,
) {
    BackHandler(onBack = leave)
    TvMoviesPage(
        films = allFilms,
        watch = watch,
        onOpenTitle = { setId ->
            restore.opened(here, setId)
            at.openTitle(setId)
        },
        restoreKey = restore.of(here),
        heldIds = heldIds,
    )
}

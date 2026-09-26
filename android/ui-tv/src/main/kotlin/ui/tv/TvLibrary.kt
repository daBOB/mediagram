package ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.CatalogUiState
import catalog.BrowseViewModel
import catalog.CatalogViewModel
import catalog.Entry
import catalog.fetchResultMessage
import system.FetchViewModel
import ui.FrameKind
import ui.LibraryPositions
import ui.rememberLibraryPositions
import ui.resolve
import ui.settings.SettingsOutcomes
import ui.tv.catalog.TvFetchResultDialog
import ui.tv.catalog.TvMenuEntryKey
import ui.tv.profile.TvChosenProfile
import ui.tv.system.TvMenuPage

/**
 * The library on a television — the twin of the phone's `LibraryFlow`: the
 * catalogue, whichever show or course it opened, whichever season of that,
 * whichever title that described, whichever set that played, whichever
 * hand-built list the Collections tab opened, search, whichever genre a
 * title's link opened, a person's own page, a franchise's own page, the
 * Genres index, the Latest page, the Movies department's own full wall, and
 * the menu with the screens it opens. Where the viewer is, and what Back
 * uncovers, is the shared [LibraryPositions] stack, asked the same way the
 * phone asks it, so the two surfaces cannot disagree about where Back goes.
 *
 * What only a television needs is [TvRestoreKeys]: each screen remembers
 * what it opened, so Back puts the remote on that plate or row again.
 * Leaving a screen goes through one `leave` that pops its frame and
 * forgets what it remembered.
 *
 * The catalogue's own saved state — which masthead tab was chosen, how far
 * its wall had scrolled — is held apart while a title or a show covers it,
 * so Back finds the tab it left rather than Home. Everything besides the
 * catalogue and the menu is dispatched to [TvLibraryCatalogFrames.kt]/
 * [TvLibraryExtraFrames.kt]: this file stays a table of what each frame
 * kind draws rather than growing a page's worth of wiring for each of them.
 */
@Composable
internal fun TvLibrary(
    profile: TvChosenProfile,
    onStartOver: () -> Unit = {},
    onSignedOut: () -> Unit = {},
) {
    val catalogViewModel: CatalogViewModel = hiltViewModel()
    val catalogState by catalogViewModel.state.collectAsStateWithLifecycle()
    val fetchViewModel: FetchViewModel = hiltViewModel()
    val fetchState by fetchViewModel.state.collectAsStateWithLifecycle()
    val browse: BrowseViewModel = hiltViewModel()
    val kidsProfile by catalogViewModel.kidsProfile.collectAsStateWithLifecycle()
    val at = rememberLibraryPositions()
    val restore = rememberTvRestoreKeys()
    val saved = rememberSaveableStateHolder()
    // The menu page stands over the shelves rather than on the positions'
    // stack: it is where System, Settings and the key screen are chosen,
    // and Back from any of them comes back to it before the masthead.
    var menuOpen by rememberSaveable { mutableStateOf(false) }

    SettingsOutcomes(onLibraryChanged = catalogViewModel::reload, onSignedOut = onSignedOut)

    val resolved = at.resolve(catalogState)
    val watch = resolved.watch
    val watchedIds = remember(watch) { watch.watched.mapTo(HashSet()) { it.setId } }
    val ready = catalogState as? CatalogUiState.Ready
    val shelves = ready?.shelves.orEmpty()
    val heldIds = ready?.heldIds.orEmpty()
    // Built once per shelves change, from shelves rather than a per-frame
    // fetch: every screen below that needs "every film"/"every show" reads
    // this, the same pool `feature:catalog`'s own pure rules already expect.
    val allFilms = remember(shelves) {
        shelves.firstOrNull { it.title == "Movies" }?.entries.orEmpty().filterIsInstance<Entry.Film>().map { it.set }
    }
    val allShows = remember(shelves) {
        shelves.asSequence().flatMap { it.entries }.filterIsInstance<Entry.Collection>().toList()
    }
    val top = at.top
    val here = at.depth
    val leave = {
        restore.forget(here)
        at.pop()
    }
    val menu = tvMenuActions(at, restore, here, catalogState, catalogViewModel, fetchState, { menuOpen = false }, onStartOver)

    when (top) {
        FrameKind.PLAYER -> TvPlayerBranch(at, catalogState, leave)

        FrameKind.MENU -> TvMenuScreenBranch(at, fetchState, fetchViewModel, leave)

        FrameKind.SEARCH -> TvSearchBranch(at, catalogState, watch, restore, browse, leave)

        FrameKind.GENRE -> TvGenreBranch(at, catalogState, watch, restore, leave)

        FrameKind.TITLE ->
            TvTitleFrame(at, catalogState, resolved.title, watch, watchedIds, allFilms, restore, here, browse, catalogViewModel, kidsProfile, leave)

        FrameKind.SEASON -> TvSeasonFrame(at, catalogState, resolved.season, watch, heldIds, restore, here, leave)

        FrameKind.COLLECTION ->
            TvCollectionFrame(at, catalogState, resolved.collection, watch, watchedIds, allShows, heldIds, restore, here, browse, catalogViewModel, leave)

        FrameKind.LIST -> TvListBranch(at, resolved.list, catalogState, catalogViewModel, restore, leave)

        FrameKind.PERSON ->
            TvPersonFrame(at, catalogState, at.personId?.toLongOrNull(), watch, heldIds, shelves, restore, here, browse, leave)

        FrameKind.FRANCHISE ->
            TvFranchiseFrame(at, catalogState, at.franchiseId?.toLongOrNull(), watch, heldIds, allFilms, restore, here, browse, leave)

        FrameKind.GENRES -> TvGenresFrame(at, shelves, restore, here, leave)

        FrameKind.LATEST -> TvLatestFrame(at, shelves, watch, heldIds, restore, here, leave)

        FrameKind.MOVIES_PAGE -> TvMoviesPageFrame(at, allFilms, watch, heldIds, restore, here, leave)

        // Nothing open, the menu page chosen from the masthead: Back from
        // it puts the remote back on the masthead's Menu.
        null if menuOpen ->
            TvMenuPage(
                menu = menu,
                restoreKey = restore.of(here),
                onMyList = {
                    menuOpen = false
                    restore.opened(here, TvWatchlistEntryKey)
                },
                onContinueWatching = {
                    menuOpen = false
                    restore.opened(here, TvContinueEntryKey)
                },
                onLatest = {
                    menuOpen = false
                    restore.forget(here)
                    at.openLatest()
                },
                onGenres = {
                    menuOpen = false
                    restore.forget(here)
                    at.openGenresIndex()
                },
            ) {
                menuOpen = false
                restore.opened(here, TvMenuEntryKey)
            }

        // Nothing open: the shelves.
        null ->
            TvLibraryHomeFrame(saved, catalogState, profile, fetchState.running, restore, here, at, catalogViewModel) { menuOpen = true }
    }

    // Every branch but the player, for the phone's reason: a result held
    // until it is dismissed is still there when the film is left, which is
    // when there is somebody to read it.
    if (top != FrameKind.PLAYER) {
        TvFetchResultDialog(
            message = fetchResultMessage(fetchState.report, fetchState.error),
            onDismiss = fetchViewModel::dismissResult,
        )
    }
}

/** The catalogue's restore key for "My List was opened from the overflow menu" — [TvMasthead]'s own sentinels' counterpart. */
internal const val TvWatchlistEntryKey = "menu:mylist"

/** The catalogue's restore key for "Continue watching was opened from the overflow menu". */
internal const val TvContinueEntryKey = "menu:continue"

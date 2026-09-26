package ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.CatalogUiState
import catalog.CatalogViewModel
import catalog.fetchResultMessage
import system.FetchViewModel
import ui.FrameKind
import ui.LibraryPositions
import ui.catalog.rememberTitleInfo
import ui.rememberLibraryPositions
import ui.resolve
import ui.tv.catalog.TvCollection
import ui.settings.SettingsOutcomes
import ui.tv.catalog.TvFetchResultDialog
import ui.tv.catalog.TvMenuEntryKey
import ui.tv.catalog.TvSearchEntryKey
import ui.tv.catalog.TvSeason
import ui.tv.catalog.TvTitlePage
import ui.tv.profile.TvChosenProfile
import ui.tv.system.TvMenuPage

/**
 * The library on a television — the twin of the phone's `LibraryFlow`: the
 * catalogue, whichever show or course it opened, whichever season of that,
 * whichever title that described, whichever set that played, whichever
 * hand-built list the Collections tab opened, search, whichever genre a
 * title's link opened, and the menu with the screens it opens. Where the viewer is, and what Back uncovers, is
 * the shared [LibraryPositions] stack, asked the same way the phone asks
 * it, so the two surfaces cannot disagree about where Back goes.
 *
 * What only a television needs is [TvRestoreKeys]: each screen remembers
 * what it opened, so Back puts the remote on that plate or row again.
 * Leaving a screen goes through one `leave` that pops its frame and
 * forgets what it remembered.
 *
 * The catalogue's own saved state — which masthead tab was chosen, how far
 * its wall had scrolled — is held apart while a title or a show covers it,
 * so Back finds the tab it left rather than Home.
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
    val heldIds = (catalogState as? CatalogUiState.Ready)?.heldIds.orEmpty()
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

        FrameKind.SEARCH -> TvSearchBranch(at, catalogState, watch, restore, leave)

        FrameKind.GENRE -> TvGenreBranch(at, catalogState, watch, restore, leave)

        FrameKind.TITLE ->
            TvResolvedBranch(resolved.title, catalogState, leave) { title ->
                TvTitlePage(
                    set = title,
                    info = rememberTitleInfo(title.posterKey, catalogViewModel::titleInfo),
                    progress = watch.progress.find { it.setId == title.setId },
                    // Coming back from the player lands on Play, not on a
                    // genre whose page was visited before it.
                    onPlay = {
                        restore.forget(here)
                        at.openPlayer(title.setId)
                    },
                    onOpenGenre = { name ->
                        restore.opened(here, name)
                        at.openGenre(name)
                    },
                    restoreKey = restore.of(here),
                )
            }

        FrameKind.SEASON ->
            TvResolvedBranch(resolved.season, catalogState, leave) { season ->
                TvSeason(
                    division = season,
                    watch = watch,
                    onOpenTitle = { setId ->
                        restore.opened(here, setId)
                        at.openTitle(setId)
                    },
                    restoreKey = restore.of(here),
                    heldIds = heldIds,
                )
            }

        FrameKind.COLLECTION ->
            TvResolvedBranch(resolved.collection, catalogState, leave) { collection ->
                TvCollection(
                    collection = collection,
                    info = rememberTitleInfo(collection.posterKey, catalogViewModel::titleInfo),
                    watch = watch,
                    posterPath = catalogViewModel::posterPath,
                    onOpenTitle = { setId ->
                        restore.opened(here, setId)
                        at.openTitle(setId)
                    },
                    onOpenSeason = { division ->
                        restore.opened(here, division.title)
                        at.openSeason(division.title)
                    },
                    onOpenGenre = { name ->
                        restore.opened(here, name)
                        at.openGenre(name)
                    },
                    restoreKey = restore.of(here),
                    heldIds = heldIds,
                )
            }

        FrameKind.LIST -> TvListBranch(at, resolved.list, catalogState, catalogViewModel, restore, leave)

        // Nothing open, the menu page chosen from the masthead: Back from
        // it puts the remote back on the masthead's Menu.
        null if menuOpen ->
            TvMenuPage(menu = menu, restoreKey = restore.of(here)) {
                menuOpen = false
                restore.opened(here, TvMenuEntryKey)
            }

        // Nothing open: the shelves.
        null -> {
            saved.SaveableStateProvider(CatalogStateKey) {
                TvCatalogRoot(
                    state = catalogState,
                    profile = profile,
                    fetching = fetchState.running,
                    restoreKey = restore.of(here),
                    onOpenTitle = { setId ->
                        restore.opened(here, setId)
                        at.openTitle(setId)
                    },
                    onOpenCollection = { key ->
                        restore.opened(here, key)
                        at.openCollection(key)
                    },
                    onOpenList = { id ->
                        restore.opened(here, id)
                        at.openList(id)
                    },
                    onCreateList = catalogViewModel::createList,
                    onTabChanged = { restore.forget(here) },
                    onOpenSearch = {
                        restore.opened(here, TvSearchEntryKey)
                        at.openSearch()
                    },
                    onOpenMenu = {
                        restore.forget(here)
                        menuOpen = true
                    },
                    onEntryRestored = { restore.forget(here) },
                    onFinish = catalogViewModel::markFinished,
                )
            }
        }
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

/** Where the catalogue's own saved state — its tab, its wall's scroll — is held while something covers it. */
private const val CatalogStateKey = "catalog"

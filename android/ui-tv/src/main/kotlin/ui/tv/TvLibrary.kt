package ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.CatalogUiState
import catalog.BrowseViewModel
import catalog.CatalogViewModel
import catalog.Department
import catalog.Entry
import catalog.everyFilm
import catalog.fetchResultMessage
import catalog.resolvableQueueRows
import designsystem.Overscan
import designsystem.Spacing
import player.LibraryPlayGate
import player.TitlePreloadViewModel
import system.FetchViewModel
import ui.common.FrameKind
import ui.common.LibraryPositions
import ui.common.catalog.LocalTogetherOffers
import ui.common.catalog.rememberTogetherOffers
import ui.common.rememberLibraryPositions
import ui.common.resolve
import ui.common.settings.SettingsOutcomes
import designsystem.LocalTvStyle
import ui.common.RailItem
import ui.tv.catalog.TvFetchResultDialog
import ui.tv.catalog.TvMenuEntryKey
import ui.tv.catalog.TvProfileEntryKey
import ui.tv.player.TvTogetherNotice
import ui.tv.profile.TvChosenProfile
import ui.tv.system.TvMenuPage

/**
 * The library on a television — the twin of the phone's `LibraryFlow`: the
 * catalogue, whichever show or course it opened, whichever title that
 * described, whichever set that played, whichever hand-built list the
 * Collections tab opened, search, whichever genre a title's link opened, a
 * person's own page, a franchise's own page, the
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
    val titlePreloadViewModel: TitlePreloadViewModel = hiltViewModel()
    val rawQueueRows by titlePreloadViewModel.queueRows.collectAsStateWithLifecycle(initialValue = emptyList())
    // The daily limit's door: a kid whose day is used up is not taken into the player.
    val playGate: LibraryPlayGate = hiltViewModel()
    val limitNotice by playGate.notice.collectAsStateWithLifecycle()
    val at = rememberLibraryPositions(playGate)
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
    // Every film across Movies and Anime alike — what a title's own Similar
    // or franchise page ranks against, the same pool `everyFilm` names on
    // the phone. `MOVIES_PAGE` below keeps its own, narrower Movies-only
    // pool: "All N films" is the Movies shelf's own wall, not a lookup.
    val allFilms = remember(shelves) { everyFilm(shelves) }
    val movieFilms = remember(shelves) {
        shelves.firstOrNull { it.department == Department.MOVIES }?.entries.orEmpty().filterIsInstance<Entry.Film>().map { it.set }
    }
    val allShows = remember(shelves) {
        shelves.asSequence().flatMap { it.entries }.filterIsInstance<Entry.Collection>().toList()
    }
    // A rail row chosen from the menu in a style with no rail, for the
    // catalogue to run the way the rail would have once it is composed again.
    var pendingRail by remember { mutableStateOf<RailItem?>(null) }
    val hasRail = LocalTvStyle.current.hasRail
    val top = at.top
    val here = at.depth
    val leave = {
        restore.forget(here)
        at.pop()
    }
    // Never the raw engine count: a kids profile must not see a
    // grown-up's own preloads counted into its own menu badge.
    val queueCount = remember(rawQueueRows, catalogState) { catalogState.resolvableQueueRows(rawQueueRows).size }
    val menu = tvMenuActions(at, restore, here, catalogState, catalogViewModel, fetchState, { menuOpen = false }, onStartOver, queueCount)

    // Join opens the session's title in the player, as each frame's own Play does: Home notes what it
    // opened, so Back comes to the plate; a title page forgets, so Back lands on Join.
    val togetherOffers =
        rememberTogetherOffers(catalogViewModel) { setId ->
            if (top == null) restore.opened(here, setId) else restore.forget(here)
            at.openPlayer(setId)
        }
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalTogetherOffers provides togetherOffers) {
            when (top) {
                FrameKind.PLAYER -> TvPlayerBranch(at, catalogState, leave)

                FrameKind.MENU -> TvMenuScreenBranch(at, fetchState, fetchViewModel, leave)

                FrameKind.SEARCH -> TvSearchBranch(at, catalogState, watch, restore, browse, leave)

                FrameKind.GENRE -> TvGenreBranch(at, catalogState, watch, restore, leave)

                FrameKind.TITLE ->
                    TvTitleFrame(at, catalogState, resolved.title, watch, watchedIds, allFilms, restore, here, browse, catalogViewModel, kidsProfile, leave)

                FrameKind.COLLECTION ->
                    TvCollectionFrame(at, catalogState, resolved.collection, watch, watchedIds, allShows, heldIds, restore, here, browse, catalogViewModel, kidsProfile, leave)

                FrameKind.LIST -> TvListBranch(at, resolved.list, catalogState, catalogViewModel, restore, leave)

                FrameKind.PERSON ->
                    TvPersonFrame(at, catalogState, at.personId, watch, heldIds, shelves, restore, here, browse, leave)

                FrameKind.FRANCHISE ->
                    TvFranchiseFrame(at, catalogState, at.franchiseId, watch, heldIds, allFilms, restore, here, browse, leave)

                FrameKind.GENRES -> TvGenresFrame(at, shelves, restore, here, leave)

                FrameKind.LATEST -> TvLatestFrame(at, shelves, watch, heldIds, restore, here, leave)

                FrameKind.MOVIES_PAGE -> TvMoviesPageFrame(at, movieFilms, watch, heldIds, restore, here, leave)

                FrameKind.PRELOADS -> TvPreloadsFrame(at, catalogState, restore, here, leave)

                FrameKind.STATS -> TvStatsFrame(catalogState, leave)

                // Nothing open, the trimmed menu page opened from the bar's avatar:
                // Back from it puts the remote back on that button.
                null if menuOpen ->
                    TvMenuPage(
                        menu = menu,
                        restoreKey = restore.of(here),
                        watchingAs = profile.name,
                        // The picker replaces the library; noting the avatar first is
                        // what lets "Stay as I am" hand the remote back to it.
                        onSwitchProfile = {
                            menuOpen = false
                            restore.opened(here, TvProfileEntryKey)
                            profile.onChoose()
                        },
                        railItems = if (hasRail) emptyList() else RailItem.entries,
                        onRailItem = { item ->
                            when (item) {
                                // Opened over this page, so Back comes back to it, as from the rail.
                                RailItem.SETTINGS -> menu.onSettings()
                                RailItem.SYSTEM -> menu.onSystem()
                                else -> {
                                    menuOpen = false
                                    // Drops the key Settings or System left, which would read as Back from them.
                                    restore.forget(here)
                                    pendingRail = item
                                }
                            }
                        },
                    ) {
                        menuOpen = false
                        restore.opened(here, TvMenuEntryKey)
                    }

                // Nothing open: the shelves.
                null ->
                    TvLibraryHomeFrame(saved, catalogState, profile, fetchState.running, restore, here, at, catalogViewModel, menu, pendingRail, { pendingRail = null }) { menuOpen = true }
            }
        }
        // The pill watch together's notices use, over the page: words to read, no focus, so the remote stays where it was.
        TvTogetherNotice(limitNotice, Modifier.align(Alignment.BottomCenter).padding(bottom = Overscan.vertical + Spacing.large))
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

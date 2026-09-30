package ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.SaveableStateHolder
import catalog.CatalogUiState
import catalog.CatalogViewModel
import catalog.mediaSet
import catalog.runFor
import ui.LibraryPositions
import ui.MenuActions
import ui.tv.catalog.TvCatalogScreen
import ui.tv.catalog.TvGenresRailKey
import ui.tv.catalog.TvLatestRailKey
import ui.tv.catalog.TvSearchEntryKey
import ui.tv.player.TvPlayerScreen
import ui.tv.profile.TvChosenProfile

/**
 * The catalogue at the top of the library — a thin pass-through onto
 * [TvCatalogScreen], kept apart from [TvLibraryHomeFrame] for the same
 * reason that composable already is: a dispatcher of its own. Back at the
 * catalogue's own root is [TvCatalogScreen]'s own chrome's business now —
 * content leaves for the selected pill, the bar leaves for the rail, the
 * rail is left unhandled so the app closes — not a step this composable
 * has to add on top of it.
 */
@Composable
internal fun TvCatalogRoot(
    state: CatalogUiState,
    profile: TvChosenProfile,
    fetching: Boolean,
    restoreKey: String?,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onOpenList: (id: String) -> Unit,
    onCreateList: (name: String) -> Unit,
    onFinish: (setId: String) -> Unit,
    menu: MenuActions = MenuActions(onSystem = {}, onSettings = {}, onUpdate = {}, onTmdbKey = {}, onStartOver = {}),
    onTabChanged: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenMenu: () -> Unit = {},
    onOpenLatest: () -> Unit = {},
    onOpenGenresIndex: () -> Unit = {},
    onEntryRestored: () -> Unit = {},
    onOpenGenre: (name: String) -> Unit = {},
    onOpenFranchise: (id: Long) -> Unit = {},
    onOpenMoviesPage: () -> Unit = {},
    onPlay: (setId: String) -> Unit = onOpenTitle,
    onToggleWatchlist: (setId: String, listed: Boolean) -> Unit = { _, _ -> },
) {
    TvCatalogScreen(
        state = state,
        profile = profile,
        onOpenTitle = onOpenTitle,
        onOpenCollection = onOpenCollection,
        onOpenList = onOpenList,
        onCreateList = onCreateList,
        menu = menu,
        fetching = fetching,
        restoreKey = restoreKey,
        onTabChanged = onTabChanged,
        onOpenSearch = onOpenSearch,
        onOpenMenu = onOpenMenu,
        onOpenLatest = onOpenLatest,
        onOpenGenresIndex = onOpenGenresIndex,
        onEntryRestored = onEntryRestored,
        onFinish = onFinish,
        onOpenGenre = onOpenGenre,
        onOpenFranchise = onOpenFranchise,
        onOpenMoviesPage = onOpenMoviesPage,
        onPlay = onPlay,
        onToggleWatchlist = onToggleWatchlist,
    )
}

/**
 * The player, over the run its title belongs to — the phone's own rule:
 * an explicit run (a list) wins, and everything else works its own out
 * from the catalogue, a title's collection or nothing for a film. A switch
 * to another title of it replaces this frame rather than stacking on it,
 * so Back still leaves to whatever opened the player.
 *
 * The player answers Back itself: the first press puts its controls away,
 * and only a press with them already gone leaves.
 */
@Composable
internal fun TvPlayerBranch(
    at: LibraryPositions,
    catalogState: CatalogUiState,
    leave: () -> Unit,
) {
    val setId = at.setId ?: return
    val set = catalogState.mediaSet(setId)
    val run = at.run ?: set?.let { runFor(it, catalogState) }.orEmpty()
    TvPlayerScreen(
        setId = setId, set = set, run = run, handPicked = at.run != null,
        onBack = leave, onSwitch = at::replacePlayer,
    )
}

/**
 * The shelves — [TvLibrary]'s own catalogue frame, kept apart from its
 * dispatcher for the same reason [TvCatalogRoot] already is, and now always
 * composed by [TvHomeLayer] whether or not something is pushed over it.
 * [saved] holds [TvCatalogRoot]'s own state — which tab was chosen, how far
 * its wall had scrolled — apart from the rest of the library's, so Back
 * finds the tab it left rather than Home once whatever covered it is gone.
 *
 * [catalogState] is frozen at whatever it was the moment [TvHomeLayer]
 * covers this frame ([heldWhile]): a position save mid playback, or a
 * catalogue refresh landing while a title is open, would otherwise
 * recompose every wall and row under here for a state nothing can see, and
 * risk racing whatever those effects do once uncovered — released back to
 * the live value the instant the cover lifts.
 */
@Composable
internal fun TvLibraryHomeFrame(
    saved: SaveableStateHolder,
    catalogState: CatalogUiState,
    profile: TvChosenProfile,
    fetching: Boolean,
    restore: TvRestoreKeys,
    here: Int,
    at: LibraryPositions,
    catalogViewModel: CatalogViewModel,
    menu: MenuActions,
    onOpenMenu: () -> Unit,
) {
    val covered = LocalLibraryCovered.current
    val frozenState = heldWhile(covered, catalogState)
    saved.SaveableStateProvider(CatalogStateKey) {
        TvCatalogRoot(
            state = frozenState,
            profile = profile,
            fetching = fetching,
            restoreKey = restore.of(here),
            menu = menu,
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
                onOpenMenu()
            },
            onOpenLatest = {
                restore.opened(here, TvLatestRailKey)
                at.openLatest()
            },
            onOpenGenresIndex = {
                restore.opened(here, TvGenresRailKey)
                at.openGenresIndex()
            },
            onEntryRestored = { restore.forget(here) },
            onFinish = catalogViewModel::markFinished,
            onOpenGenre = { name ->
                restore.opened(here, name)
                at.openGenre(name)
            },
            onOpenFranchise = { id ->
                restore.opened(here, id.toString())
                at.openFranchise(id.toString())
            },
            onOpenMoviesPage = {
                restore.opened(here, TvMoviesPageEntryKey)
                at.openMoviesPage()
            },
            // The cover story's own "Watch now" — straight to the player,
            // the same as `web/home-cover.js:137`, rather than the title
            // page every other plate on this screen opens.
            onPlay = { setId ->
                restore.opened(here, setId)
                at.openPlayer(setId)
            },
            onToggleWatchlist = catalogViewModel::setWatchlisted,
        )
    }
}

/** Where [TvCatalogRoot]'s own saved state — its tab, its wall's scroll — is held while something covers it. */
private const val CatalogStateKey = "catalog"

/** The catalogue's restore key for ""All N films" was opened from the Movies department" — no plate of its own to remember instead. */
internal const val TvMoviesPageEntryKey = "movies:all"

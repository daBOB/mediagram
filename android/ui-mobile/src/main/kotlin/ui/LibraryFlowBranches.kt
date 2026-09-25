package ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import catalog.CatalogUiState
import catalog.CatalogViewModel
import catalog.mediaSet
import catalog.runFor
import system.FetchUiState
import system.FetchViewModel
import ui.player.PlayerScreen
import ui.system.SystemScreen
import ui.settings.TmdbKeyScreen
import ui.settings.SettingsScreen
import ui.settings.CacheSection
import ui.catalog.SearchBranch
import ui.catalog.GenreBranch
import ui.catalog.TitleDetailScreen
import ui.catalog.rememberTitleInfo
import ui.catalog.SeasonScreen
import ui.catalog.CollectionScreen
import ui.catalog.ListScreen
import ui.catalog.CatalogScreen

/**
 * The library's own screens, one for each [FrameKind] — dispatched on
 * [LibraryPositions.top] alone, which is what lets a screen opened over
 * another leave back to it rather than to whatever is under both: a title
 * opened from a genre page leaves to that genre page, and a genre page
 * opened from a title leaves to that title, however deep either goes. Split
 * out of [LibraryFlow] once its own `when` outgrew that file.
 */
@Composable
internal fun LibraryBranches(
    at: LibraryPositions,
    catalogState: CatalogUiState,
    catalogViewModel: CatalogViewModel,
    fetchState: FetchUiState,
    fetchViewModel: FetchViewModel,
    menuActions: MenuActions,
    profileBar: ProfileBarState,
) {
    val resolved = at.resolve(catalogState)

    when (at.top) {
        // The player gets the whole window; a film is the one thing here
        // that wants the space under the system bars.
        FrameKind.PLAYER -> {
            val setId = at.setId ?: return
            val set = catalogState.mediaSet(setId)
            // An explicit run (a list, or the Kids marked-by-hand wall) wins;
            // everything else works its own out from the catalog — a title's
            // own collection, or nothing for a film.
            val run = at.run ?: set?.let { runFor(it, catalogState) }.orEmpty()
            BackHandler(onBack = at::pop)
            PlayerScreen(setId = setId, run = run, fsk = set?.fsk, onBack = at::pop, onSwitch = at::replacePlayer)
        }

        FrameKind.MENU -> {
            val menuScreen = at.menuScreen ?: return
            LibraryBranch(menuScreen.destination, menuActions, profileBar, at, at::pop) {
                when (menuScreen) {
                    MenuScreen.System -> SystemScreen()
                    MenuScreen.TmdbKey -> TmdbKeyScreen(hasKey = fetchState.hasKey, onSave = fetchViewModel::saveKey)
                    // Stands in until the real screen lands.
                    MenuScreen.Settings -> SettingsScreen(cache = { CacheSection() })
                }
            }
        }

        FrameKind.SEARCH -> {
            val search = at.search ?: return
            LibraryBranch(Destination.Search, menuActions, profileBar, at, at::pop) {
                SearchBranch(
                    query = search,
                    catalogState = catalogState,
                    watch = resolved.watch,
                    onQueryChange = { at.typeSearch(it) },
                    onPlay = at::openPlayer,
                )
            }
        }

        FrameKind.GENRE -> {
            val genre = at.genre ?: return
            LibraryBranch(Destination.Genre(genre), menuActions, profileBar, at, at::pop) {
                GenreBranch(
                    name = genre,
                    catalogState = catalogState,
                    watch = resolved.watch,
                    onOpenTitle = at::openTitle,
                    onOpenCollection = at::openCollection,
                )
            }
        }

        FrameKind.TITLE -> ResolvedBranch(resolved.title, catalogState, Destination.Title(LOADING), menuActions, profileBar, at, { Destination.Title(it.title) }) { title ->
            TitleDetailScreen(
                set = title,
                info = rememberTitleInfo(title.posterKey, catalogViewModel::titleInfo),
                onPlay = { at.openPlayer(title.setId) },
                onOpenGenre = at::openGenre,
            )
        }

        FrameKind.SEASON -> ResolvedBranch(resolved.season, catalogState, Destination.Season(LOADING), menuActions, profileBar, at, { Destination.Season(it.title) }) { season ->
            SeasonScreen(division = season, watch = resolved.watch, heldIds = catalogState.heldIdsOrEmpty(), onOpenTitle = at::openTitle)
        }

        FrameKind.COLLECTION -> ResolvedBranch(resolved.collection, catalogState, Destination.Collection(LOADING), menuActions, profileBar, at, { Destination.Collection(it.name) }) { collection ->
            CollectionScreen(
                collection = collection,
                info = rememberTitleInfo(collection.posterKey, catalogViewModel::titleInfo),
                watch = resolved.watch,
                heldIds = catalogState.heldIdsOrEmpty(),
                posterPath = catalogViewModel::posterPath,
                onOpenTitle = at::openTitle,
                onOpenSeason = { at.openSeason(it.title) },
                onOpenGenre = at::openGenre,
            )
        }

        // A hand-built list, opened from the Collections tab — a peer of
        // the collection branch above rather than something under it: a
        // list is never reached through the catalog shelves.
        FrameKind.LIST -> ResolvedBranch(resolved.list, catalogState, Destination.List(LOADING), menuActions, profileBar, at, { Destination.List(it.name) }) { list ->
            val sets = list.items.mapNotNull(catalogState::mediaSet)
            val ids = sets.map { it.setId }
            // Every row plays into the list, not just from where it was
            // tapped onward — the same run either way, `nextInQueue` walks
            // forward from wherever a viewer started.
            ListScreen(
                list = list,
                sets = sets,
                onPlay = { setId -> at.openPlayer(setId, ids) },
                onPlayAll = sets.firstOrNull()?.let { first -> { at.openPlayer(first.setId, ids) } },
                onRename = { name -> catalogViewModel.renameList(list.id, name) },
                onDelete = { catalogViewModel.deleteList(list.id); at.pop() },
                onRemove = { removedId -> catalogViewModel.setInList(list.id, removedId, false) },
                heldIds = catalogState.heldIdsOrEmpty(),
            )
        }

        // Nothing open: the shelves.
        null -> LibraryScaffold(
            destination = Destination.Catalog,
            // Never invoked: LibraryScaffold only wires onBack up when
            // backLabelFor(Destination.Catalog) says there is a way back,
            // and there is not — the catalog is the top of the tree.
            onBack = {},
            menu = menuActions,
            profile = profileBar,
            onSearch = at::openSearch,
        ) {
            CatalogScreen(
                state = catalogState,
                fetching = fetchState.running,
                onOpenTitle = at::openTitle,
                onOpenCollection = at::openCollection,
                onOpenList = at::openList,
                onCreateList = catalogViewModel::createList,
                onPlayRun = at::openPlayer,
                onFinish = { catalogViewModel.markFinished(it) },
            )
        }
    }
}

/**
 * One screen of the library under the app's chrome, and what leaving it
 * means.
 *
 * The system back gesture and the bar's back arrow are the same departure
 * said twice, so they are given the same lambda here rather than at each
 * branch — a screen that wired one and forgot the other would go back in
 * two different places depending on which the viewer reached for.
 *
 * Takes [at] itself rather than an `onSearch` lambda: every branch opens
 * search the same way — pushed over whatever it was already showing — so
 * there is nothing left for a caller to decide.
 */
@Composable
internal fun LibraryBranch(
    destination: Destination,
    menu: MenuActions,
    profile: ProfileBarState,
    at: LibraryPositions,
    onLeave: () -> Unit,
    content: @Composable () -> Unit,
) {
    BackHandler(onBack = onLeave)
    LibraryScaffold(
        destination = destination,
        onBack = onLeave,
        menu = menu,
        profile = profile,
        onSearch = at::openSearch,
        content = content,
    )
}

/** What this device holds in full, or nothing while the shelves are still loading. */
private fun CatalogUiState.heldIdsOrEmpty(): Set<String> = (this as? CatalogUiState.Ready)?.heldIds.orEmpty()

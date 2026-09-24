package ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import catalog.CatalogUiState
import catalog.CatalogViewModel
import catalog.mediaSet
import system.FetchUiState
import system.FetchViewModel

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
            BackHandler(onBack = at::pop)
            PlayerScreen(setId = setId, fsk = catalogState.mediaSet(setId)?.fsk, onBack = at::pop)
        }

        FrameKind.MENU -> {
            val menuScreen = at.menuScreen ?: return
            LibraryBranch(menuScreen.destination, menuActions, profileBar, at, at::pop) {
                when (menuScreen) {
                    MenuScreen.System -> SystemScreen()
                    MenuScreen.TmdbKey -> TmdbKeyScreen(hasKey = fetchState.hasKey, onSave = fetchViewModel::saveKey)
                    // Stands in until the real screen lands.
                    MenuScreen.Settings -> SettingsScreen(cache = { CacheBudgetBlock() })
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
            SeasonScreen(division = season, watch = resolved.watch, onOpenTitle = at::openTitle)
        }

        FrameKind.COLLECTION -> ResolvedBranch(resolved.collection, catalogState, Destination.Collection(LOADING), menuActions, profileBar, at, { Destination.Collection(it.name) }) { collection ->
            CollectionScreen(
                collection = collection,
                info = rememberTitleInfo(collection.posterKey, catalogViewModel::titleInfo),
                watch = resolved.watch,
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
            ListScreen(
                list = list,
                sets = list.items.mapNotNull(catalogState::mediaSet),
                onPlay = at::openPlayer,
                onRename = { name -> catalogViewModel.renameList(list.id, name) },
                onDelete = { catalogViewModel.deleteList(list.id); at.pop() },
                onRemove = { removedId -> catalogViewModel.setInList(list.id, removedId, false) },
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


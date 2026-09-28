package ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import catalog.CatalogTabs
import catalog.CatalogUiState
import catalog.CatalogViewModel
import catalog.Destination
import catalog.HOME
import catalog.Shelf
import catalog.catalogTabsOf
import catalog.libraryTallyLines
import catalog.magazineHomeOf
import catalog.mediaSet
import catalog.runFor
import model.WatchSnapshot
import system.FetchUiState
import system.FetchViewModel
import ui.catalog.CatalogScreen
import ui.catalog.GenreBranch
import ui.catalog.ListScreen
import ui.catalog.SearchBranch
import ui.catalog.SeasonScreen
import ui.catalog.posterColumnsFor
import ui.catalog.visibleTabIndices
import ui.chrome.LibraryHome
import ui.chrome.LocalRailData
import ui.chrome.RailData
import ui.chrome.chromeCountsOf
import ui.player.PlayerScreen

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
    // The shelves' own remembered state — which tab, which page of films, how
    // far down — outlives a title, collection or the player opened over them,
    // so coming back finds the shelf as it was left, as the web's back button does.
    val shelvesState = rememberSaveableStateHolder()
    val columns = posterColumnsFor(currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass)

    // Which of catalogTabsOf's full index space the shelves screen shows —
    // lifted up here (rather than kept inside CatalogScreen) so the rail's
    // own My List/Continue watching rows can land on it from anywhere, the
    // same way the web's rail-nav can. See [BrowseActions].
    //
    // Persisted by title, not by the plain index a tab sits at: a shelf list
    // gaining or losing a department shifts every later tab's index, and an
    // index saved across a process restart (a rotation, or the OS reclaiming
    // memory while this task sits in recents) would then restore onto
    // whichever tab now sits at that number rather than the one that was
    // actually left open.
    var chosenTabTitle by rememberSaveable { mutableStateOf(HOME) }
    val shelves = (catalogState as? CatalogUiState.Ready)?.shelves.orEmpty()
    val watch = (catalogState as? CatalogUiState.Ready)?.watch ?: WatchSnapshot.Empty
    // firstKept, not a hand-counted offset: a shelf list gaining or losing a
    // department must not silently point My List and Continue watching at
    // the wrong tab.
    val fullTabs = remember(shelves) { catalogTabsOf(shelves) }
    val visible = remember(fullTabs) { visibleTabIndices(fullTabs) }
    val chosenTab = restoredTabIndex(fullTabs, chosenTabTitle)
    val chooseTab = { index: Int -> chosenTabTitle = fullTabs.titles.getOrElse(index) { HOME } }
    val browse = BrowseActions(
        onMyList = { at.toCatalog(); chooseTab(fullTabs.firstKept + 1) },
        onContinueWatching = { at.toCatalog(); chooseTab(fullTabs.firstKept) },
        onLatest = at::openLatest,
        onGenres = at::openGenresIndex,
    )
    // The rail's own counts and tally, and its wordmark's "go home" — read
    // fresh from the shelves wherever the rail renders (root or pushed
    // frame alike) through [LocalRailData], rather than threaded through
    // every branch below that never otherwise needs them.
    val railData =
        remember(shelves, watch) {
            RailData(chromeCountsOf(shelves, watch), libraryTallyLines(shelves), onHome = { at.toCatalog(); chooseTab(0) })
        }

    // Home's own list state, hoisted here rather than kept inside
    // CatalogScreen/HomeScreen — LibraryHome's departments bar reads its
    // scroll position for the over-cover blend, and a state this function
    // does not itself compose past never gets lost when the width class
    // switches (see LibraryHome's own note on the single content call site).
    val homeListState = rememberLazyListState()
    // Shared with the same call CatalogScreen makes so the two can never
    // pick different editorial sets from two different moments — see
    // CatalogScreen's own note on `now`.
    val now = remember { System.currentTimeMillis() }
    val heldIds = catalogState.heldIdsOrEmpty()
    val hasCover =
        remember(shelves, watch, heldIds, now) {
            magazineHomeOf(shelves, watch, editorsChoice = watch.editorsChoice, now = now, heldIds = heldIds).editorial.cover.isNotEmpty()
        }

    CompositionLocalProvider(LocalRailData provides railData) {
    when (at.top) {
        // The player gets the whole window; a film is the one thing here
        // that wants the space under the system bars.
        // `?.let` rather than `?: return`: these branches now sit inside
        // CompositionLocalProvider's own content lambda, which is not
        // inline — a bare `return` there does not compile. `let` is
        // inline, so calling it does not need the fix at all; using it
        // here anyway reads the same as the early return did, and behaves
        // the same, since nothing follows this `when` either way.
        FrameKind.PLAYER -> at.setId?.let { setId ->
            val set = catalogState.mediaSet(setId)
            // An explicit run (a list, or the Kids marked-by-hand wall) wins;
            // everything else works its own out from the catalog — a title's
            // own collection, or nothing for a film.
            val run = at.run ?: set?.let { runFor(it, catalogState) }.orEmpty()
            BackHandler(onBack = at::pop)
            PlayerScreen(
                setId = setId, run = run, fsk = set?.fsk, handPicked = at.run != null,
                onBack = at::pop, onSwitch = at::replacePlayer,
            )
        }

        FrameKind.MENU -> at.menuScreen?.let { menuScreen ->
            MenuBranch(menuScreen, at, catalogState, fetchState, fetchViewModel, menuActions, profileBar, browse)
        }

        FrameKind.SEARCH -> at.search?.let { search ->
            LibraryBranch(Destination.Search, menuActions, profileBar, browse, at, at::pop) {
                SearchBranch(
                    query = search,
                    catalogState = catalogState,
                    watch = resolved.watch,
                    onQueryChange = { at.typeSearch(it) },
                    onPlay = at::openPlayer,
                    onOpenTitle = at::openTitle,
                    onOpenCollection = at::openCollection,
                    onOpenPerson = { id -> at.openPerson(id.toString()) },
                    onOpenFranchise = { id -> at.openFranchise(id.toString()) },
                    onOpenList = at::openList,
                )
            }
        }

        FrameKind.GENRE -> at.genre?.let { genre ->
            LibraryBranch(Destination.Genre(genre), menuActions, profileBar, browse, at, at::pop) {
                GenreBranch(
                    name = genre,
                    catalogState = catalogState,
                    watch = resolved.watch,
                    onOpenTitle = at::openTitle,
                    onOpenCollection = at::openCollection,
                )
            }
        }

        FrameKind.TITLE -> TitleFrame(at, catalogState, catalogViewModel, resolved, menuActions, profileBar, browse)

        FrameKind.SEASON -> ResolvedBranch(resolved.season, catalogState, Destination.Season(LOADING), menuActions, profileBar, browse, at, { Destination.Season(it.title) }) { season ->
            SeasonScreen(division = season, watch = resolved.watch, heldIds = catalogState.heldIdsOrEmpty(), onOpenTitle = at::openTitle)
        }

        FrameKind.COLLECTION -> CollectionFrame(at, catalogState, catalogViewModel, resolved, menuActions, profileBar, browse)

        // A hand-built list, opened from the Collections tab — a peer of
        // the collection branch above rather than something under it: a
        // list is never reached through the catalog shelves.
        FrameKind.LIST -> ResolvedBranch(resolved.list, catalogState, Destination.List(LOADING), menuActions, profileBar, browse, at, { Destination.List(it.name) }) { list ->
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

        FrameKind.PERSON -> PersonFrame(at, catalogState, resolved.watch, menuActions, profileBar, browse)
        FrameKind.FRANCHISE -> FranchiseFrame(at, catalogState, resolved.watch, columns, menuActions, profileBar, browse)
        FrameKind.GENRES -> GenresFrame(at, catalogState, columns, menuActions, profileBar, browse)
        FrameKind.LATEST -> LatestFrame(at, catalogState, resolved.watch, columns, menuActions, profileBar, browse)
        FrameKind.MOVIES_PAGE -> MoviesPageFrame(at, catalogState, catalogViewModel, resolved.watch, columns, menuActions, profileBar, browse)

        // Nothing open: the shelves, under the rail/departments-bar chrome
        // rather than LibraryScaffold — see [ui.chrome.LibraryHome].
        null -> LibraryHome(
            tabs = fullTabs,
            visible = visible,
            chosenTab = chosenTab,
            onTabChange = chooseTab,
            browse = browse,
            menu = menuActions,
            profile = profileBar,
            onSearch = at::openSearch,
            homeScrollState = homeListState,
            hasCover = hasCover,
        ) {
            shelvesState.SaveableStateProvider(SHELVES_KEY) { CatalogScreen(
                state = catalogState,
                fetching = fetchState.running,
                chosenTab = chosenTab,
                onTabChange = chooseTab,
                onOpenTitle = at::openTitle,
                onOpenCollection = at::openCollection,
                onOpenList = at::openList,
                onCreateList = catalogViewModel::createList,
                onOpenGenre = at::openGenre,
                onOpenGenresIndex = at::openGenresIndex,
                onOpenLatest = at::openLatest,
                onOpenMoviesPage = at::openMoviesPage,
                onOpenFranchise = { id -> at.openFranchise(id.toString()) },
                onPlayRun = at::openPlayer,
                onFinish = { catalogViewModel.markFinished(it) },
                onToggleWatchlist = catalogViewModel::setWatchlisted,
                homeListState = homeListState,
                now = now,
                titleInfo = catalogViewModel::titleInfo,
            ) }
        }
    }
    }
}

private const val SHELVES_KEY = "shelves"

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
    browse: BrowseActions,
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
        browse = browse,
        onSearch = at::openSearch,
        content = content,
    )
}

/**
 * Which of [tabs]' own tabs [title] names, or Home when it names none.
 *
 * A title saved before a shelf list shift (Documentaries landing between
 * Series and Tutorials, or any future department) no longer matches
 * anything at its old index, so restoring by the plain index would reopen
 * on whichever tab now sits there instead of the one that was actually
 * left open. A title survives the shift; only a title this build no longer
 * has at all — a stale save, or nothing chosen yet — falls back to Home.
 */
internal fun restoredTabIndex(tabs: CatalogTabs, title: String): Int = tabs.titles.indexOf(title).takeIf { it >= 0 } ?: 0

/** What this device holds in full, or nothing while the shelves are still loading. */
internal fun CatalogUiState.heldIdsOrEmpty(): Set<String> = (this as? CatalogUiState.Ready)?.heldIds.orEmpty()

/** The shelves a title or a collection page ranks Similar/a franchise link against, or nothing while still loading. */
internal fun CatalogUiState.shelvesOrEmpty(): List<Shelf> = (this as? CatalogUiState.Ready)?.shelves.orEmpty()

package ui

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.BrowseViewModel
import catalog.CatalogUiState
import catalog.CatalogViewModel
import catalog.Destination
import catalog.Entry
import catalog.ShelfViewModel
import catalog.allTitles
import catalog.franchisePageOf
import catalog.genreIndex
import catalog.personPageOf
import model.MediaSet
import model.WatchSnapshot
import ui.catalog.CenteredMessage
import ui.catalog.FilmShelfActions
import ui.catalog.FranchiseScreen
import ui.catalog.GenresIndexScreen
import ui.catalog.LatestScreen
import ui.catalog.PersonScreen
import ui.catalog.ShelfViewChoice
import ui.catalog.ShelfWall
import ui.catalog.rememberFranchiseOverviews
import ui.catalog.rememberPerson
import ui.catalog.rememberPortrait

/**
 * The five browse frames this phase adds — [FrameKind.PERSON],
 * [FrameKind.FRANCHISE], [FrameKind.GENRES], [FrameKind.LATEST] and
 * [FrameKind.MOVIES_PAGE] — split out of [LibraryBranches] once its own
 * `when` outgrew that file the same way [ResolvedBranch] once did.
 */
@Composable
internal fun FranchiseFrame(
    at: LibraryPositions,
    catalogState: CatalogUiState,
    watch: WatchSnapshot,
    columns: Int,
    menuActions: MenuActions,
    profileBar: ProfileBarState,
    browse: BrowseActions,
) {
    val id = at.franchiseId?.toLongOrNull()
    if (id == null) {
        LaunchedEffect(Unit) { at.pop() }
        return
    }
    val browseViewModel: BrowseViewModel = hiltViewModel()
    val overviews = rememberFranchiseOverviews(browseViewModel::franchiseOverviews)
    val movies = moviesOf(catalogState)
    val page = remember(id, movies, overviews) { franchisePageOf(id, movies, overviews) }
    ResolvedBranch(page, catalogState, Destination.Franchise(LOADING), menuActions, profileBar, browse, at, { Destination.Franchise(it.franchise.name) }) { resolvedPage ->
        FranchiseScreen(page = resolvedPage, watch = watch, columns = columns, onOpenTitle = at::openTitle)
    }
}

@Composable
internal fun GenresFrame(
    at: LibraryPositions,
    catalogState: CatalogUiState,
    columns: Int,
    menuActions: MenuActions,
    profileBar: ProfileBarState,
    browse: BrowseActions,
) {
    val shelves = (catalogState as? CatalogUiState.Ready)?.shelves
    LibraryBranch(Destination.Genres, menuActions, profileBar, browse, at, at::pop) {
        if (shelves == null) {
            CenteredMessage("Loading your library…")
        } else {
            val genres = remember(shelves) { genreIndex(allTitles(shelves)) }
            GenresIndexScreen(genres = genres, columns = columns, onOpenGenre = at::openGenre)
        }
    }
}

@Composable
internal fun LatestFrame(
    at: LibraryPositions,
    catalogState: CatalogUiState,
    watch: WatchSnapshot,
    columns: Int,
    menuActions: MenuActions,
    profileBar: ProfileBarState,
    browse: BrowseActions,
) {
    val shelves = (catalogState as? CatalogUiState.Ready)?.shelves
    LibraryBranch(Destination.Latest, menuActions, profileBar, browse, at, at::pop) {
        if (shelves == null) {
            CenteredMessage("Loading your library…")
        } else {
            LatestScreen(shelves, watch, catalogState.heldIdsOrEmpty(), columns, at::openTitle, at::openCollection)
        }
    }
}

/** "All N films": the Movies department's own paged shelf, one step in from its front page — reuses [ShelfWall] exactly as the plain shelf once did. */
@Composable
internal fun MoviesPageFrame(
    at: LibraryPositions,
    catalogState: CatalogUiState,
    catalogViewModel: CatalogViewModel,
    watch: WatchSnapshot,
    columns: Int,
    menuActions: MenuActions,
    profileBar: ProfileBarState,
    browse: BrowseActions,
) {
    val shelf = (catalogState as? CatalogUiState.Ready)?.shelves?.firstOrNull { it.title == "Movies" }
    LibraryBranch(Destination.MoviesPage, menuActions, profileBar, browse, at, at::pop) {
        if (shelf == null) {
            CenteredMessage("Loading your library…")
        } else {
            val shelfViewModel: ShelfViewModel = hiltViewModel()
            val chosenView by shelfViewModel.view.collectAsStateWithLifecycle()
            ShelfWall(
                shelf = shelf,
                watch = watch,
                heldIds = catalogState.heldIdsOrEmpty(),
                columns = columns,
                view = ShelfViewChoice(chosenView, shelfViewModel::choose),
                onOpenTitle = at::openTitle,
                onOpenCollection = at::openCollection,
                films = FilmShelfActions(onPlay = { at.openPlayer(it.setId) }, titleInfo = catalogViewModel::titleInfo),
            )
        }
    }
}

/** The Movies shelf's own films, from an already-ready [catalogState] — a franchise page's own input. */
private fun moviesOf(catalogState: CatalogUiState): List<MediaSet> =
    (catalogState as? CatalogUiState.Ready)?.shelves?.firstOrNull { it.title == "Movies" }?.entries
        ?.filterIsInstance<Entry.Film>()?.map { it.set }.orEmpty()

/**
 * A person's page — resolved in two steps, unlike [ResolvedBranch]'s one:
 * [catalog.Person] itself is an async lookup ([rememberPerson]), not
 * something [catalogState] already has synchronously the way a title or a
 * collection is, so "still fetching" and "asked and nobody by that id"
 * cannot both read as the same `null`. [attempted] is what tells them apart.
 */
@Composable
internal fun PersonFrame(
    at: LibraryPositions,
    catalogState: CatalogUiState,
    watch: WatchSnapshot,
    menuActions: MenuActions,
    profileBar: ProfileBarState,
    browse: BrowseActions,
) {
    val id = at.personId?.toLongOrNull()
    if (id == null) {
        LaunchedEffect(Unit) { at.pop() }
        return
    }
    val columns = posterColumnsForCurrentWindow()
    val browseViewModel: BrowseViewModel = hiltViewModel()
    var attempted by remember(id) { mutableStateOf(false) }
    val person = rememberPerson(id) { personId ->
        try {
            browseViewModel.person(personId)
        } finally {
            attempted = true
        }
    }
    val shelves = (catalogState as? CatalogUiState.Ready)?.shelves

    when {
        !attempted || shelves == null -> LibraryBranch(Destination.Person(LOADING), menuActions, profileBar, browse, at, at::pop) {
            CenteredMessage("Loading your library…")
        }
        else -> {
            val page = remember(person, shelves) { personPageOf(person, shelves) }
            if (page == null) {
                LibraryBranch(Destination.Person("Person"), menuActions, profileBar, browse, at, at::pop) {
                    CenteredMessage("Nobody by that number is credited on anything in your library.")
                }
            } else {
                val portrait = rememberPortrait(id, page.person.portraitPath, browseViewModel::shouldRequestPortrait, browseViewModel::fetchPortrait)
                LibraryBranch(Destination.Person(page.person.name), menuActions, profileBar, browse, at, at::pop) {
                    PersonScreen(page, portrait, watch, columns, at::openTitle, at::openCollection)
                }
            }
        }
    }
}

@Composable
private fun posterColumnsForCurrentWindow(): Int =
    ui.catalog.posterColumnsFor(currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass)

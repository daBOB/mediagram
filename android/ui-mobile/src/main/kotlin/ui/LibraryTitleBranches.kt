package ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.BrowseViewModel
import catalog.CatalogUiState
import catalog.CatalogViewModel
import catalog.Destination
import catalog.MenuScreen
import catalog.firstItemOf
import model.Kind
import ui.catalog.CollectionScreen
import ui.catalog.TitleDetailScreen
import ui.catalog.rememberTitleInfo

/**
 * A title's own page and a show or course's own page — split out of
 * [LibraryBranches]'s own `when` once its own setup grew past what fit
 * beside a dispatch this wide, the same way [PersonFrame] and its
 * neighbours already did in `LibraryBrowseBranches.kt`.
 */
@Composable
internal fun TitleFrame(
    at: LibraryPositions,
    catalogState: CatalogUiState,
    catalogViewModel: CatalogViewModel,
    resolved: ResolvedPositions,
    menuActions: MenuActions,
    profileBar: ProfileBarState,
    browse: BrowseActions,
) {
    ResolvedBranch(resolved.title, catalogState, Destination.Title(LOADING), menuActions, profileBar, browse, at, { Destination.Title(it.title) }) { title ->
        val kidsProfile by catalogViewModel.kidsProfile.collectAsStateWithLifecycle()
        val browseViewModel: BrowseViewModel = hiltViewModel()
        TitleDetailScreen(
            set = title,
            info = rememberTitleInfo(title.posterKey, catalogViewModel::titleInfo),
            onPlay = { at.openPlayer(title.setId) },
            onOpenGenre = at::openGenre,
            editorsChoice = resolved.watch.editorsChoice,
            onToggleEditorsChoice =
                if (kidsProfile) {
                    null
                } else {
                    { catalogViewModel.setEditorsChoice(title.setId, resolved.watch.editorsChoice != title.setId) }
                },
            watch = resolved.watch,
            shelves = catalogState.shelvesOrEmpty(),
            onOpenTitle = at::openTitle,
            onOpenFranchise = { id -> at.openFranchise(id.toString()) },
            onOpenPerson = { id -> at.openPerson(id.toString()) },
            onToggleWatchlist = { catalogViewModel.setWatchlisted(title.setId, title.setId !in resolved.watch.watchlist) },
            titleCredits = catalogViewModel::titleCredits,
            fetchPortrait = browseViewModel::fetchPortrait,
            shouldRequestPortrait = browseViewModel::shouldRequestPortrait,
            // Films only — a show's episodes preload two at a time on
            // their own already; kids profiles get it too, unlike the
            // editor's-choice pin above, since it is not a household mark.
            preload = if (title.kind == Kind.MOVIE) rememberFilmPreloadUi(title) { at.openMenu(MenuScreen.Storage) } else null,
        )
    }
}

@Composable
internal fun CollectionFrame(
    at: LibraryPositions,
    catalogState: CatalogUiState,
    catalogViewModel: CatalogViewModel,
    resolved: ResolvedPositions,
    menuActions: MenuActions,
    profileBar: ProfileBarState,
    browse: BrowseActions,
) {
    ResolvedBranch(resolved.collection, catalogState, Destination.Collection(LOADING), menuActions, profileBar, browse, at, { Destination.Collection(it.name) }) { collection ->
        val kidsProfile by catalogViewModel.kidsProfile.collectAsStateWithLifecycle()
        val browseViewModel: BrowseViewModel = hiltViewModel()
        // A show's first episode stands for the whole show, the same way
        // its own page's pills already key "My List"/editor's choice off
        // it (see `CollectionScreen`'s own `firstEpisode`); a course has
        // none of these controls, so a null first episode never matters here.
        val firstEpisodeId = firstItemOf(collection.divisions)?.setId
        CollectionScreen(
            collection = collection,
            info = rememberTitleInfo(collection.posterKey, catalogViewModel::titleInfo),
            watch = resolved.watch,
            heldIds = catalogState.heldIdsOrEmpty(),
            onOpenTitle = at::openTitle,
            onOpenSeason = { at.openSeason(it.title) },
            onOpenGenre = at::openGenre,
            shelves = catalogState.shelvesOrEmpty(),
            onOpenCollection = at::openCollection,
            onOpenPerson = { id -> at.openPerson(id.toString()) },
            onPlay = at::openPlayer,
            editorsChoice = resolved.watch.editorsChoice,
            onToggleEditorsChoice =
                if (kidsProfile || firstEpisodeId == null) {
                    null
                } else {
                    { catalogViewModel.setEditorsChoice(firstEpisodeId, resolved.watch.editorsChoice != firstEpisodeId) }
                },
            onToggleWatchlist = {
                firstEpisodeId?.let { catalogViewModel.setWatchlisted(it, it !in resolved.watch.watchlist) }
            },
            titleCredits = catalogViewModel::titleCredits,
            fetchPortrait = browseViewModel::fetchPortrait,
            shouldRequestPortrait = browseViewModel::shouldRequestPortrait,
            season = at.collectionSeason,
            onSelectSeason = at::setCollectionSeason,
        )
    }
}

package ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import catalog.BrowseViewModel
import catalog.CatalogUiState
import catalog.CatalogViewModel
import catalog.CollectionKind
import catalog.Entry
import catalog.MenuScreen
import catalog.firstItemOf
import catalog.seriesResumeFor
import catalog.similarShows
import catalog.similarTo
import model.Kind
import model.MediaSet
import model.WatchSnapshot
import ui.LibraryPositions
import ui.catalog.rememberTitleCredits
import ui.catalog.rememberTitleInfo
import ui.tv.catalog.TvCollection
import ui.tv.catalog.TvTitlePage
import ui.tv.catalog.franchiseRestoreKey

/**
 * The film and show/course frames — [TvLibrary]'s two biggest branches,
 * split out here so that file stays a dispatcher rather than growing a
 * page's worth of wiring for each of them.
 */
@Composable
internal fun TvTitleFrame(
    at: LibraryPositions,
    catalogState: CatalogUiState,
    title: MediaSet?,
    watch: WatchSnapshot,
    watchedIds: Set<String>,
    allFilms: List<MediaSet>,
    restore: TvRestoreKeys,
    here: Int,
    browse: BrowseViewModel,
    catalogViewModel: CatalogViewModel,
    kidsProfile: Boolean,
    leave: () -> Unit,
) {
    TvResolvedBranch(title, catalogState, leave) { set ->
        val credits = rememberTitleCredits(set.posterKey, catalogViewModel::titleCredits)
        val similar = remember(set, allFilms, watchedIds) { similarTo(set, allFilms) { it.setId in watchedIds } }
        TvTitlePage(
            set = set,
            info = rememberTitleInfo(set.posterKey, catalogViewModel::titleInfo),
            progress = watch.progress.find { it.setId == set.setId },
            // Coming back from the player lands on Play, not on a genre or a
            // person whose page was visited before it.
            onPlay = {
                restore.forget(here)
                at.openPlayer(set.setId)
            },
            onOpenGenre = { name ->
                restore.opened(here, name)
                at.openGenre(name)
            },
            restoreKey = restore.of(here),
            credits = credits,
            onOpenPerson = { personId ->
                restore.opened(here, personId.toString())
                at.openPerson(personId)
            },
            shouldRequestPortrait = browse::shouldRequestPortrait,
            fetchPortrait = browse::fetchPortrait,
            similar = similar,
            onOpenTitle = { setId ->
                restore.opened(here, setId)
                at.openTitle(setId)
            },
            allFilms = allFilms,
            onOpenFranchise = { id ->
                restore.opened(here, franchiseRestoreKey(id))
                at.openFranchise(id)
            },
            editorsChoice = watch.editorsChoice,
            onToggleEditorsChoice =
                if (kidsProfile) {
                    null
                } else {
                    { catalogViewModel.toggleEditorsChoice(set.setId) }
                },
            // Films only, same as the phone's own TitleDetailScreen — a
            // show's episodes preload two at a time on their own already.
            preload = if (set.kind == Kind.MOVIE) rememberTvFilmPreloadUi(set, catalogState) { at.openMenu(MenuScreen.Storage) } else null,
            watchlisted = set.setId in watch.watchlist,
            onToggleWatchlist = { catalogViewModel.toggleWatchlist(set.setId) },
        )
    }
}

@Composable
internal fun TvCollectionFrame(
    at: LibraryPositions,
    catalogState: CatalogUiState,
    collection: Entry.Collection?,
    watch: WatchSnapshot,
    watchedIds: Set<String>,
    allShows: List<Entry.Collection>,
    heldIds: Set<String>,
    restore: TvRestoreKeys,
    here: Int,
    browse: BrowseViewModel,
    catalogViewModel: CatalogViewModel,
    kidsProfile: Boolean,
    leave: () -> Unit,
) {
    TvResolvedBranch(collection, catalogState, leave) { coll ->
        // A show is listed and pinned by its first episode, as the web's page does.
        val firstEpisodeId = remember(coll) { firstItemOf(coll.divisions)?.setId }
        // A course's page draws no facts, cast or similar courses, so it asks for none.
        val show = coll.kind == CollectionKind.SHOW
        val providerKey = coll.posterKey.takeIf { show }
        val credits = rememberTitleCredits(providerKey, catalogViewModel::titleCredits)
        val similar = remember(coll, allShows, watchedIds) { if (show) similarShows(coll, allShows) { id -> id in watchedIds } else emptyList() }
        val resume = remember(coll, watch) { seriesResumeFor(coll, watch) }
        TvCollection(
            collection = coll,
            info = rememberTitleInfo(providerKey, catalogViewModel::titleInfo),
            watch = watch,
            onPlay = { setId ->
                restore.opened(here, setId)
                at.openPlayer(setId)
            },
            onOpenGenre = { name ->
                restore.opened(here, name)
                at.openGenre(name)
            },
            restoreKey = restore.of(here),
            heldIds = heldIds,
            credits = credits,
            onOpenPerson = { personId ->
                restore.opened(here, personId.toString())
                at.openPerson(personId)
            },
            shouldRequestPortrait = browse::shouldRequestPortrait,
            fetchPortrait = browse::fetchPortrait,
            similar = similar,
            onOpenCollection = { key ->
                restore.opened(here, key)
                at.openCollection(key)
            },
            resume = resume,
            onResume = { setId ->
                restore.forget(here)
                at.openPlayer(setId)
            },
            season = at.collectionSeason,
            onSelectSeason = at::setCollectionSeason,
            onToggleWatchlist = { firstEpisodeId?.let(catalogViewModel::toggleWatchlist) },
            editorsChoice = watch.editorsChoice,
            onToggleEditorsChoice =
                if (kidsProfile || firstEpisodeId == null) {
                    null
                } else {
                    { catalogViewModel.toggleEditorsChoice(firstEpisodeId) }
                },
        )
    }
}

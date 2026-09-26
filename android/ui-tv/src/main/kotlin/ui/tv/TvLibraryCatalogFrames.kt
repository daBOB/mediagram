package ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import catalog.BrowseViewModel
import catalog.CatalogUiState
import catalog.CatalogViewModel
import catalog.Division
import catalog.Entry
import catalog.seriesResumeFor
import catalog.similarShows
import catalog.similarTo
import model.MediaSet
import model.WatchSnapshot
import ui.LibraryPositions
import ui.catalog.rememberTitleCredits
import ui.catalog.rememberTitleInfo
import ui.tv.catalog.TvCollection
import ui.tv.catalog.TvSeason
import ui.tv.catalog.TvTitlePage

/**
 * The film, season and show/course frames — [TvLibrary]'s three biggest
 * branches, split out here so that file stays a dispatcher rather than
 * growing a page's worth of wiring for each of them.
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
                at.openPerson(personId.toString())
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
                restore.opened(here, id.toString())
                at.openFranchise(id.toString())
            },
            editorsChoice = watch.editorsChoice,
            onToggleEditorsChoice =
                if (kidsProfile) {
                    null
                } else {
                    { catalogViewModel.setEditorsChoice(set.setId, watch.editorsChoice != set.setId) }
                },
        )
    }
}

@Composable
internal fun TvSeasonFrame(
    at: LibraryPositions,
    catalogState: CatalogUiState,
    season: Division?,
    watch: WatchSnapshot,
    heldIds: Set<String>,
    restore: TvRestoreKeys,
    here: Int,
    leave: () -> Unit,
) {
    TvResolvedBranch(season, catalogState, leave) { division ->
        TvSeason(
            division = division,
            watch = watch,
            onOpenTitle = { setId ->
                restore.opened(here, setId)
                at.openTitle(setId)
            },
            restoreKey = restore.of(here),
            heldIds = heldIds,
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
    leave: () -> Unit,
) {
    TvResolvedBranch(collection, catalogState, leave) { coll ->
        val credits = rememberTitleCredits(coll.posterKey, catalogViewModel::titleCredits)
        val similar = remember(coll, allShows, watchedIds) { similarShows(coll, allShows) { id -> id in watchedIds } }
        val resume = remember(coll, watch) { seriesResumeFor(coll, watch) }
        TvCollection(
            collection = coll,
            info = rememberTitleInfo(coll.posterKey, catalogViewModel::titleInfo),
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
            credits = credits,
            onOpenPerson = { personId ->
                restore.opened(here, personId.toString())
                at.openPerson(personId.toString())
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
        )
    }
}

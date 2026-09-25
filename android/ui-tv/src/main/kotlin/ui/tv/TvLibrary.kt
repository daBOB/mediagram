package ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.CatalogViewModel
import catalog.ResolvedPosition
import catalog.fetchResultMessage
import catalog.mediaSet
import catalog.watchSnapshot
import system.FetchViewModel
import ui.catalog.rememberTitleInfo
import ui.rememberLibraryPositions
import ui.tv.catalog.TvCollection
import ui.tv.catalog.TvFetchResultDialog
import ui.tv.catalog.TvList
import ui.tv.catalog.TvSeason
import ui.tv.catalog.TvTitlePage
import ui.tv.profile.TvChosenProfile

/**
 * The library on a television — the twin of the phone's `LibraryFlow`: the
 * catalogue, whichever show or course it opened, whichever season of that,
 * whichever title that described, whichever set that played, and whichever
 * hand-built list the Collections tab opened. Where the viewer is, which of
 * those wins and what Back clears are the shared position model's, asked
 * the same way the phone asks it, so the two surfaces cannot disagree about
 * where Back goes.
 *
 * What only a television needs is [TvRestoreKeys]: each screen remembers
 * what it opened, so Back puts the remote on that plate or row again.
 * Leaving a screen goes through one [leave] that clears both its position
 * and what it remembered.
 *
 * The catalogue's own saved state — which masthead tab was chosen, how far
 * its wall had scrolled — is held apart while a title or a show covers it,
 * so Back finds the tab it left rather than Home.
 */
@Composable
internal fun TvLibrary(profile: TvChosenProfile) {
    val catalogViewModel: CatalogViewModel = hiltViewModel()
    val catalogState by catalogViewModel.state.collectAsStateWithLifecycle()
    val fetchViewModel: FetchViewModel = hiltViewModel()
    val fetchState by fetchViewModel.state.collectAsStateWithLifecycle()
    val at = rememberLibraryPositions()
    val restore = rememberTvRestoreKeys()
    val saved = rememberSaveableStateHolder()

    val resolved = at.snapshot().resolve(catalogState)
    val watch = catalogState.watchSnapshot()
    val leave = { from: ResolvedPosition ->
        placeOf(from)?.let { restore.forget(it) }
        at.leaveFrom(from)
    }

    when (resolved) {
        is ResolvedPosition.Player -> {
            BackHandler { leave(resolved) }
            TvPlayerStandIn(catalogState.mediaSet(resolved.setId)?.title)
        }

        // Nothing on a television opens a menu screen yet, so a saved one
        // can only be left over — and drawing nothing for it would strand
        // the viewer on a blank page Back could not see past. Leaving it
        // uncovers whatever it was laid over.
        is ResolvedPosition.Menu -> {
            LaunchedEffect(resolved) { leave(resolved) }
        }

        is ResolvedPosition.TitleOpen -> {
            val title = resolved.title
            BackHandler { leave(resolved) }
            TvTitlePage(
                set = title,
                info = rememberTitleInfo(title.posterKey, catalogViewModel::titleInfo),
                progress = watch.progress.find { it.setId == title.setId },
                onPlay = { at.setId = title.setId },
            )
        }

        is ResolvedPosition.SeasonOpen -> {
            BackHandler { leave(resolved) }
            TvSeason(
                division = resolved.season,
                watch = watch,
                onOpenTitle = { setId ->
                    restore.opened(TvPlace.Season, setId)
                    at.titleId = setId
                },
                restoreKey = restore.of(TvPlace.Season),
            )
        }

        is ResolvedPosition.CollectionOpen -> {
            val collection = resolved.collection
            BackHandler { leave(resolved) }
            TvCollection(
                collection = collection,
                info = rememberTitleInfo(collection.posterKey, catalogViewModel::titleInfo),
                watch = watch,
                posterPath = catalogViewModel::posterPath,
                onOpenTitle = { setId ->
                    restore.opened(TvPlace.Collection, setId)
                    at.titleId = setId
                },
                onOpenSeason = { division ->
                    restore.opened(TvPlace.Collection, division.title)
                    at.season = division.title
                },
                restoreKey = restore.of(TvPlace.Collection),
            )
        }

        // A list plays straight from its plate, as the phone's does: it is
        // a viewer's own pick, already chosen, not a shelf to browse.
        is ResolvedPosition.ListOpen -> {
            val list = resolved.list
            BackHandler { leave(resolved) }
            TvList(
                list = list,
                sets = list.items.mapNotNull(catalogState::mediaSet),
                onPlay = { setId ->
                    restore.opened(TvPlace.List, setId)
                    at.setId = setId
                },
                onRename = { name -> catalogViewModel.renameList(list.id, name) },
                onDelete = {
                    catalogViewModel.deleteList(list.id)
                    leave(resolved)
                },
                onRemove = { setId -> catalogViewModel.setInList(list.id, setId, false) },
                restoreKey = restore.of(TvPlace.List),
            )
        }

        // Also where a saved key lands while the library is still loading,
        // and where one that no longer names anything stays — see the
        // phone's own branch for why that is the right thing to show.
        ResolvedPosition.Catalog -> {
            saved.SaveableStateProvider(TvPlace.Catalog.name) {
                TvCatalogRoot(
                    state = catalogState,
                    profile = profile,
                    fetching = fetchState.running,
                    restoreKey = restore.of(TvPlace.Catalog),
                    onOpenTitle = { setId ->
                        restore.opened(TvPlace.Catalog, setId)
                        at.titleId = setId
                    },
                    // A season or a remembered plate left from another show
                    // would otherwise land on this one: "Season 1" is not a
                    // fact about one show.
                    onOpenCollection = { key ->
                        restore.opened(TvPlace.Catalog, key)
                        restore.forget(TvPlace.Collection, TvPlace.Season)
                        at.collection = key
                        at.season = null
                    },
                    onOpenList = { id ->
                        restore.opened(TvPlace.Catalog, id)
                        restore.forget(TvPlace.List)
                        at.listId = id
                    },
                    onCreateList = catalogViewModel::createList,
                )
            }
        }
    }

    // Every branch but the player, for the phone's reason: a result held
    // until it is dismissed is still there when the film is left, which is
    // when there is somebody to read it.
    if (resolved !is ResolvedPosition.Player) {
        TvFetchResultDialog(
            message = fetchResultMessage(fetchState.report, fetchState.error),
            onDismiss = fetchViewModel::dismissResult,
        )
    }
}

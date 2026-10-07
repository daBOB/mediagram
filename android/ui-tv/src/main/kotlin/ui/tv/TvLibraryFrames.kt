package ui.tv

import catalog.BrowseViewModel
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import catalog.CatalogUiState
import catalog.CatalogViewModel
import catalog.franchiseHref
import catalog.mediaSet
import model.ListOfSets
import model.WatchSnapshot
import ui.common.FrameResolution
import ui.common.LibraryPositions
import ui.common.resolveFrame
import ui.tv.catalog.TvGenre
import ui.tv.catalog.TvList
import ui.tv.catalog.TvPlayAllKey
import ui.tv.catalog.TvSearch
import ui.tv.catalog.destinationKey
import ui.tv.catalog.personKey
import ui.tv.catalog.showKey
import ui.tv.setup.TvLoadingIndicator

/**
 * Search, over whatever it was opened from. A film's poster opens its page
 * and a matched show, a person or a collection destination each open their
 * own, over search, the same way a title's genre link does; an episode or
 * lesson row plays straight away, as on the phone and the web. Back lands
 * on whichever entry was pressed.
 */
@Composable
internal fun TvSearchBranch(
    at: LibraryPositions,
    catalogState: CatalogUiState,
    watch: WatchSnapshot,
    restore: TvRestoreKeys,
    browse: BrowseViewModel,
    leave: () -> Unit,
) {
    val here = at.depth
    BackHandler(onBack = leave)
    TvSearch(
        query = at.search.orEmpty(),
        catalogState = catalogState,
        watch = watch,
        restoreKey = restore.of(here),
        onQueryChange = at::typeSearch,
        onPlay = { setId ->
            restore.opened(here, setId)
            at.openPlayer(setId)
        },
        onOpenTitle = { setId ->
            restore.opened(here, setId)
            at.openTitle(setId)
        },
        // Recorded under the results' own row keys ([showKey], [personKey] and
        // [destinationKey], which `keyOf` builds them with), so Back finds the
        // show, person or collection that was opened rather than the first result.
        onOpenCollection = { key ->
            restore.opened(here, showKey(key))
            at.openCollection(key)
        },
        onOpenPerson = { personId ->
            restore.opened(here, personKey(personId))
            at.openPerson(personId)
        },
        onOpenFranchise = { id ->
            restore.opened(here, destinationKey(franchiseHref(id)))
            at.openFranchise(id)
        },
        onOpenList = { id ->
            restore.opened(here, destinationKey(id))
            at.openList(id)
        },
        portraits = browse.portraits,
        fetchPortrait = browse::fetchPortrait,
    )
}

/** The genre a title's link opened, over that title; Back lands on the plate that was opened from it. */
@Composable
internal fun TvGenreBranch(
    at: LibraryPositions,
    catalogState: CatalogUiState,
    watch: WatchSnapshot,
    restore: TvRestoreKeys,
    leave: () -> Unit,
) {
    val here = at.depth
    BackHandler(onBack = leave)
    TvGenre(
        name = at.genre.orEmpty(),
        catalogState = catalogState,
        watch = watch,
        onOpenTitle = { setId ->
            restore.opened(here, setId)
            at.openTitle(setId)
        },
        onOpenCollection = { key ->
            restore.opened(here, key)
            at.openCollection(key)
        },
        restoreKey = restore.of(here),
    )
}

/**
 * A screen whose key is looked up against the catalogue, by the phone's own
 * rule ([resolveFrame]): drawn once it resolves, a loading indicator while
 * the catalogue has not answered yet — a restore landing here before the
 * library has loaded — and left at once when the catalogue has answered and
 * the key still names nothing, a list deleted on another device.
 */
@Composable
internal fun <T> TvResolvedBranch(
    resolved: T?,
    catalogState: CatalogUiState,
    leave: () -> Unit,
    content: @Composable (T) -> Unit,
) {
    when (val outcome = resolveFrame(resolved, catalogState is CatalogUiState.Ready)) {
        is FrameResolution.Resolved -> {
            BackHandler(onBack = leave)
            content(outcome.value)
        }
        FrameResolution.Loading -> {
            BackHandler(onBack = leave)
            TvLoadingIndicator()
        }
        FrameResolution.Stale -> LaunchedEffect(Unit) { leave() }
    }
}

/**
 * A hand-built list, which plays straight from its plate, as the phone's
 * does: it is a viewer's own pick, already chosen, not a shelf to browse.
 * Every plate plays into the whole list, the same run from wherever a
 * viewer started; `nextInQueue` walks on from there.
 */
@Composable
internal fun TvListBranch(
    at: LibraryPositions,
    resolved: ListOfSets?,
    catalogState: CatalogUiState,
    catalogViewModel: CatalogViewModel,
    restore: TvRestoreKeys,
    leave: () -> Unit,
) {
    val here = at.depth
    TvResolvedBranch(resolved, catalogState, leave) { list ->
        val sets = list.items.mapNotNull(catalogState::mediaSet)
        val ids = sets.map { it.setId }
        TvList(
            list = list,
            sets = sets,
            onPlay = { setId ->
                restore.opened(here, setId)
                at.openPlayer(setId, ids)
            },
            onPlayAll = {
                ids.firstOrNull()?.let { first ->
                    restore.opened(here, TvPlayAllKey)
                    at.openPlayer(first, ids)
                }
            },
            onRename = { name -> catalogViewModel.renameList(list.id, name) },
            onDelete = {
                catalogViewModel.deleteList(list.id)
                leave()
            },
            onRemove = { setId -> catalogViewModel.setInList(list.id, setId, false) },
            restoreKey = restore.of(here),
            heldIds = (catalogState as? CatalogUiState.Ready)?.heldIds.orEmpty(),
        )
    }
}

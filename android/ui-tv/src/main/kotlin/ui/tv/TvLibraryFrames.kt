package ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import catalog.CatalogUiState
import catalog.CatalogViewModel
import catalog.mediaSet
import model.ListOfSets
import model.WatchSnapshot
import ui.FrameResolution
import ui.LibraryPositions
import ui.resolveFrame
import ui.tv.catalog.TvCatalogExtras
import ui.tv.catalog.TvGenre
import ui.tv.catalog.TvList
import ui.tv.catalog.TvPlayAllKey
import ui.tv.catalog.TvSearch
import ui.tv.setup.TvLoadingIndicator

/**
 * Search, over whatever it was opened from. A hit plays straight away, as
 * on the phone, and Back from the player lands on the row that played. A
 * matched show, a person or a collection destination each open their own
 * page instead, over search, the same way a title's genre link does.
 */
@Composable
internal fun TvSearchBranch(
    at: LibraryPositions,
    catalogState: CatalogUiState,
    watch: WatchSnapshot,
    restore: TvRestoreKeys,
    extras: TvCatalogExtras,
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
        onOpenCollection = { key ->
            restore.opened(here, key)
            at.openCollection(key)
        },
        onOpenPerson = { personId ->
            restore.opened(here, personId.toString())
            at.openPerson(personId.toString())
        },
        onOpenFranchise = { id ->
            restore.opened(here, id.toString())
            at.openFranchise(id.toString())
        },
        onOpenList = { id ->
            restore.opened(here, id)
            at.openList(id)
        },
        shouldRequestPortrait = extras::shouldRequestPortrait,
        fetchPortrait = extras::fetchPortrait,
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

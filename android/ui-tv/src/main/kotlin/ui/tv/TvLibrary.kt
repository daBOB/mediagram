package ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.CatalogUiState
import catalog.CatalogViewModel
import catalog.fetchResultMessage
import catalog.mediaSet
import system.FetchViewModel
import ui.FrameKind
import ui.FrameResolution
import ui.LibraryPositions
import ui.catalog.rememberTitleInfo
import ui.rememberLibraryPositions
import ui.resolve
import ui.resolveFrame
import ui.tv.catalog.TvCollection
import ui.tv.catalog.TvFetchResultDialog
import ui.tv.catalog.TvList
import ui.tv.catalog.TvSeason
import ui.tv.catalog.TvTitlePage
import ui.tv.profile.TvChosenProfile
import ui.tv.setup.TvLoadingIndicator

/**
 * The library on a television — the twin of the phone's `LibraryFlow`: the
 * catalogue, whichever show or course it opened, whichever season of that,
 * whichever title that described, whichever set that played, and whichever
 * hand-built list the Collections tab opened. Where the viewer is, and what
 * Back uncovers, is the shared [LibraryPositions] stack, asked the same way
 * the phone asks it, so the two surfaces cannot disagree about where Back
 * goes.
 *
 * What only a television needs is [TvRestoreKeys]: each screen remembers
 * what it opened, so Back puts the remote on that plate or row again.
 * Leaving a screen goes through one `leave` that pops its frame and
 * forgets what it remembered.
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

    val resolved = at.resolve(catalogState)
    val watch = resolved.watch
    val top = at.top
    val here = at.depth
    val leave = {
        restore.forget(here)
        at.pop()
    }

    when (top) {
        FrameKind.PLAYER -> TvPlayerBranch(at, catalogState, leave)

        // Nothing on a television opens a menu screen, search or a genre
        // page yet, so a saved one can only be left over — and drawing
        // nothing for it would strand the viewer on a blank page Back could
        // not see past. Leaving it uncovers whatever it was laid over.
        FrameKind.MENU, FrameKind.SEARCH, FrameKind.GENRE -> {
            LaunchedEffect(top) { leave() }
        }

        FrameKind.TITLE ->
            TvResolvedBranch(resolved.title, catalogState, leave) { title ->
                TvTitlePage(
                    set = title,
                    info = rememberTitleInfo(title.posterKey, catalogViewModel::titleInfo),
                    progress = watch.progress.find { it.setId == title.setId },
                    onPlay = { at.openPlayer(title.setId) },
                )
            }

        FrameKind.SEASON ->
            TvResolvedBranch(resolved.season, catalogState, leave) { season ->
                TvSeason(
                    division = season,
                    watch = watch,
                    onOpenTitle = { setId ->
                        restore.opened(here, setId)
                        at.openTitle(setId)
                    },
                    restoreKey = restore.of(here),
                )
            }

        FrameKind.COLLECTION ->
            TvResolvedBranch(resolved.collection, catalogState, leave) { collection ->
                TvCollection(
                    collection = collection,
                    info = rememberTitleInfo(collection.posterKey, catalogViewModel::titleInfo),
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
                    restoreKey = restore.of(here),
                )
            }

        // A list plays straight from its plate, as the phone's does: it is
        // a viewer's own pick, already chosen, not a shelf to browse. Every
        // plate plays into the whole list, the same run from wherever a
        // viewer started; `nextInQueue` walks on from there.
        FrameKind.LIST ->
            TvResolvedBranch(resolved.list, catalogState, leave) { list ->
                val sets = list.items.mapNotNull(catalogState::mediaSet)
                val ids = sets.map { it.setId }
                TvList(
                    list = list,
                    sets = sets,
                    onPlay = { setId ->
                        restore.opened(here, setId)
                        at.openPlayer(setId, ids)
                    },
                    onPlayAll = { ids.firstOrNull()?.let { first -> at.openPlayer(first, ids) } },
                    onRename = { name -> catalogViewModel.renameList(list.id, name) },
                    onDelete = {
                        catalogViewModel.deleteList(list.id)
                        leave()
                    },
                    onRemove = { setId -> catalogViewModel.setInList(list.id, setId, false) },
                    restoreKey = restore.of(here),
                )
            }

        // Nothing open: the shelves.
        null -> {
            saved.SaveableStateProvider(CatalogStateKey) {
                TvCatalogRoot(
                    state = catalogState,
                    profile = profile,
                    fetching = fetchState.running,
                    restoreKey = restore.of(here),
                    onOpenTitle = { setId ->
                        restore.opened(here, setId)
                        at.openTitle(setId)
                    },
                    onOpenCollection = { key ->
                        restore.opened(here, key)
                        at.openCollection(key)
                    },
                    onOpenList = { id ->
                        restore.opened(here, id)
                        at.openList(id)
                    },
                    onCreateList = catalogViewModel::createList,
                    onTabChanged = { restore.forget(here) },
                )
            }
        }
    }

    // Every branch but the player, for the phone's reason: a result held
    // until it is dismissed is still there when the film is left, which is
    // when there is somebody to read it.
    if (top != FrameKind.PLAYER) {
        TvFetchResultDialog(
            message = fetchResultMessage(fetchState.report, fetchState.error),
            onDismiss = fetchViewModel::dismissResult,
        )
    }
}

/**
 * A screen whose key is looked up against the catalogue, by the phone's own
 * rule ([resolveFrame]): drawn once it resolves, a loading indicator while
 * the catalogue has not answered yet — a restore landing here before the
 * library has loaded — and left at once when the catalogue has answered and
 * the key still names nothing, a list deleted on another device.
 */
@Composable
private fun <T> TvResolvedBranch(
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

/** Where the catalogue's own saved state — its tab, its wall's scroll — is held while something covers it. */
private const val CatalogStateKey = "catalog"

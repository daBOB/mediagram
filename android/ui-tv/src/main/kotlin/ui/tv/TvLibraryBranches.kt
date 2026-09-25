package ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import catalog.CatalogUiState
import catalog.mediaSet
import catalog.runFor
import ui.LibraryPositions
import ui.tv.catalog.TvCatalogScreen
import ui.tv.player.TvPlayerScreen
import ui.tv.profile.TvChosenProfile

/**
 * The catalogue at the top of the library, where Back has nowhere further
 * in to go: the first press takes the remote up to the masthead, the way a
 * television app's Back first backs out of its content, and a press with
 * the remote already there is left alone so the app closes, as Back at the
 * top of any app does. Without the first step, Back from deep in a wall
 * would close the app on a viewer who only meant to go up a level.
 */
@Composable
internal fun TvCatalogRoot(
    state: CatalogUiState,
    profile: TvChosenProfile,
    fetching: Boolean,
    restoreKey: String?,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onOpenList: (id: String) -> Unit,
    onCreateList: (name: String) -> Unit,
    onTabChanged: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenMenu: () -> Unit,
    onEntryRestored: () -> Unit,
    onFinish: (setId: String) -> Unit,
) {
    val masthead = remember { FocusRequester() }
    var onMasthead by remember { mutableStateOf(false) }
    // Composed ahead of the screen, so a Back handler a screen inside it
    // registers — naming a new list — is asked first.
    BackHandler(enabled = !onMasthead) { masthead.requestFocus() }
    TvCatalogScreen(
        state = state,
        profile = profile,
        onOpenTitle = onOpenTitle,
        onOpenCollection = onOpenCollection,
        onOpenList = onOpenList,
        onCreateList = onCreateList,
        mastheadFocus = masthead,
        fetching = fetching,
        restoreKey = restoreKey,
        onMastheadFocusChanged = { onMasthead = it },
        onTabChanged = onTabChanged,
        onOpenSearch = onOpenSearch,
        onOpenMenu = onOpenMenu,
        onEntryRestored = onEntryRestored,
        onFinish = onFinish,
    )
}

/**
 * The player, over the run its title belongs to — the phone's own rule:
 * an explicit run (a list) wins, and everything else works its own out
 * from the catalogue, a title's collection or nothing for a film. A switch
 * to another title of it replaces this frame rather than stacking on it,
 * so Back still leaves to whatever opened the player.
 *
 * The player answers Back itself: the first press puts its controls away,
 * and only a press with them already gone leaves.
 */
@Composable
internal fun TvPlayerBranch(
    at: LibraryPositions,
    catalogState: CatalogUiState,
    leave: () -> Unit,
) {
    val setId = at.setId ?: return
    val set = catalogState.mediaSet(setId)
    val run = at.run ?: set?.let { runFor(it, catalogState) }.orEmpty()
    TvPlayerScreen(setId = setId, set = set, run = run, onBack = leave, onSwitch = at::replacePlayer)
}

package ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import catalog.CatalogUiState
import ui.tv.catalog.TvCatalogScreen
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
    )
}

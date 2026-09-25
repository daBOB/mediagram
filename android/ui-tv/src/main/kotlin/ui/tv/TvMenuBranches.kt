package ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import catalog.CatalogUiState
import catalog.CatalogViewModel
import catalog.MenuScreen
import catalog.updateDisabledReason
import system.FetchUiState
import system.FetchViewModel
import ui.LibraryPositions
import ui.MenuActions
import ui.tv.system.TvSettingsScreen
import ui.tv.system.TvSystemScreen
import ui.tv.system.TvTmdbKeyScreen
import ui.tv.system.menuRestoreKey

/**
 * The menu's five items, bound the way the phone's `LibraryFlow` binds
 * them: three open a menu screen over the page they were chosen on, so
 * Back comes back to the page with the remote on that item; Update library
 * goes to the shelves, where its progress line is and where what it
 * fetches lands, and runs the catalogue's one update; Start over is the
 * setup's own. [depth] is the page's own place on the stack, where it
 * remembers which item opened a screen.
 */
internal fun tvMenuActions(
    at: LibraryPositions,
    restore: TvRestoreKeys,
    depth: Int,
    catalogState: CatalogUiState,
    catalogViewModel: CatalogViewModel,
    fetchState: FetchUiState,
    onLeavePage: () -> Unit,
    onStartOver: () -> Unit,
): MenuActions {
    val open = { screen: MenuScreen ->
        restore.opened(depth, menuRestoreKey(screen))
        at.openMenu(screen)
    }
    return MenuActions(
        onSystem = { open(MenuScreen.System) },
        onSettings = { open(MenuScreen.Settings) },
        onUpdate = {
            onLeavePage()
            restore.forget(depth)
            at.toCatalog()
            catalogViewModel.update()
        },
        onTmdbKey = { open(MenuScreen.TmdbKey) },
        onStartOver = onStartOver,
        updateDisabledReason = updateDisabledReason(catalogState, fetchState.running),
        updateNote = if (fetchState.hasKey) null else "Artwork and descriptions need a TMDB key",
    )
}

/**
 * One of the three screens the menu opens. Back leaves it for the page it
 * was opened from; a panel inside Settings, composed after this, answers
 * Back first and only closes itself.
 *
 * A saved screen this build cannot name is left at once rather than drawn
 * as a blank page Back could not see past.
 */
@Composable
internal fun TvMenuScreenBranch(
    at: LibraryPositions,
    fetchState: FetchUiState,
    fetchViewModel: FetchViewModel,
    leave: () -> Unit,
) {
    val screen = at.menuScreen
    if (screen == null) {
        LaunchedEffect(Unit) { leave() }
        return
    }
    BackHandler(onBack = leave)
    when (screen) {
        MenuScreen.System -> TvSystemScreen()
        MenuScreen.Settings -> TvSettingsScreen()
        MenuScreen.TmdbKey -> TvTmdbKeyScreen(hasKey = fetchState.hasKey, onSave = fetchViewModel::saveKey)
    }
}

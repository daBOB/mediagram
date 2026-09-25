package ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.CatalogViewModel
import catalog.MenuScreen
import catalog.fetchResultMessage
import catalog.updateDisabledReason
import system.FetchViewModel
import ui.catalog.FetchResultDialog
import ui.profile.ProfileGate
import ui.settings.SettingsOutcomes

/**
 * The catalog, whichever show or course it opened, whichever title that
 * described, whichever set that played, whichever hand-built list the
 * Collections tab opened, whichever query search holds, whichever genre a
 * chip opened, the system screen, and the TMDB key screen — the first
 * screens here with a real back-stack need. Where those positions are kept,
 * and why, is [LibraryPositions]; which screen each resolves to is
 * [LibraryBranches]. Gated on a chosen profile by [ProfileGate], which is
 * what decides whose shelves these are.
 */
@Composable
internal fun CatalogAndPlayer(onStartOver: () -> Unit, onSignedOut: () -> Unit) {
    ProfileGate { profileBar -> Library(profileBar, onStartOver, onSignedOut) }
}

@Composable
private fun Library(profileBar: ProfileBarState, onStartOver: () -> Unit, onSignedOut: () -> Unit) {
    val catalogViewModel: CatalogViewModel = hiltViewModel()
    val catalogState by catalogViewModel.state.collectAsStateWithLifecycle()
    val fetchViewModel: FetchViewModel = hiltViewModel()
    val fetchState by fetchViewModel.state.collectAsStateWithLifecycle()
    val at = rememberLibraryPositions()

    SettingsOutcomes(onLibraryChanged = catalogViewModel::reload, onSignedOut = onSignedOut)

    val menuActions = MenuActions(
        onSystem = { at.openMenu(MenuScreen.System) },
        onSettings = { at.openMenu(MenuScreen.Settings) },
        // To the shelves, wherever the menu was opened from. The menu is the
        // same on the system and key screens, where a reloading catalog is
        // invisible; and an update is minutes of network over hundreds of
        // titles, so the shelves are both where the progress line lives and
        // where the artwork it fetches lands. You asked for the library; the
        // library is what you are shown.
        onUpdate = {
            at.toCatalog()
            // The refresh, then the fetch for what it brought home, runs as
            // one update in the catalog's own coordinator.
            catalogViewModel.update()
        },
        onTmdbKey = { at.openMenu(MenuScreen.TmdbKey) },
        onStartOver = onStartOver,
        updateDisabledReason = updateDisabledReason(catalogState, fetchState.running),
        updateNote = if (fetchState.hasKey) null else "Artwork and descriptions need a TMDB key",
    )

    LibraryBranches(at, catalogState, catalogViewModel, fetchState, fetchViewModel, menuActions, profileBar)

    // Every branch but the player, which is the one that fills the window
    // with a picture. A fetch started before a film began would otherwise
    // put its tally over the film; the result is held until it is dismissed,
    // so it is still there when the film is left, which is when there is
    // somebody to read it.
    if (at.setId == null) {
        FetchResultDialog(
            message = fetchResultMessage(fetchState.report, fetchState.error),
            onDismiss = fetchViewModel::dismissResult,
        )
    }
}

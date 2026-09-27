package ui

import androidx.compose.runtime.Composable
import catalog.CatalogUiState
import catalog.MenuScreen
import catalog.libraryTallyLines
import system.FetchUiState
import system.FetchViewModel
import ui.settings.SettingsScreen
import ui.settings.SettingsSection
import ui.settings.TmdbKeyScreen

/**
 * Settings, System and the TMDB key screen — split out of
 * [LibraryBranches]'s own `when` once it grew past what one screen's worth
 * of branches should carry. Settings/System render without
 * [LibraryScaffold] entirely (the approved mockups have no bar, and
 * [ui.settings.SettingsIndex] is their own rail-equivalent); the key screen
 * still keeps it, the same as every other pushed frame.
 */
@Composable
internal fun MenuBranch(
    menuScreen: MenuScreen,
    at: LibraryPositions,
    catalogState: CatalogUiState,
    fetchState: FetchUiState,
    fetchViewModel: FetchViewModel,
    menuActions: MenuActions,
    profileBar: ProfileBarState,
    browse: BrowseActions,
) {
    when (menuScreen) {
        MenuScreen.System ->
            SettingsScreen(
                initial = SettingsSection.SYSTEM,
                leavesFromSection = true,
                tally = libraryTallyLines(catalogState.shelvesOrEmpty()),
                onLeave = at::pop,
            )

        MenuScreen.Settings ->
            SettingsScreen(
                initial = null,
                leavesFromSection = false,
                tally = libraryTallyLines(catalogState.shelvesOrEmpty()),
                onLeave = at::pop,
            )

        // A film page's own "Raise the cache budget" link — the
        // same direct-section shape MenuScreen.System already uses.
        MenuScreen.Storage ->
            SettingsScreen(
                initial = SettingsSection.STORAGE,
                leavesFromSection = true,
                tally = libraryTallyLines(catalogState.shelvesOrEmpty()),
                onLeave = at::pop,
            )

        MenuScreen.TmdbKey ->
            LibraryBranch(menuScreen.destination, menuActions, profileBar, browse, at, at::pop) {
                TmdbKeyScreen(hasKey = fetchState.hasKey, onSave = fetchViewModel::saveKey)
            }
    }
}

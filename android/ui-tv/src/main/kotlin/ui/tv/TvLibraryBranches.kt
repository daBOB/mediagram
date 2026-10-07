package ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.CatalogUiState
import catalog.CatalogViewModel
import catalog.mediaSet
import catalog.runFor
import stats.AchievementDotViewModel
import ui.common.LibraryPositions
import ui.common.MenuActions
import ui.tv.catalog.TvCatalogScreen
import ui.tv.catalog.TvGenresRailKey
import ui.tv.catalog.TvLatestRailKey
import ui.tv.catalog.TvMoviesPageEntryKey
import ui.tv.catalog.TvSearchEntryKey
import ui.tv.catalog.TvStatsRailKey
import ui.tv.chrome.LocalNewAchievement
import ui.tv.player.TvPlayerScreen
import ui.tv.profile.TvChosenProfile

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
    TvPlayerScreen(
        setId = setId, set = set, run = run, handPicked = at.run != null,
        onBack = leave, onSwitch = at::replacePlayer,
    )
}

/**
 * The shelves, with nothing open over them — [TvLibrary]'s own "nothing
 * else is showing" frame, kept apart from its dispatcher. [saved] holds
 * [TvCatalogScreen]'s own state — which tab was chosen, how far its wall
 * had scrolled — apart from the rest of the library's, so Back finds the
 * tab it left rather than Home once whatever covered it is gone.
 */
@Composable
internal fun TvLibraryHomeFrame(
    saved: SaveableStateHolder,
    catalogState: CatalogUiState,
    profile: TvChosenProfile,
    fetching: Boolean,
    restore: TvRestoreKeys,
    here: Int,
    at: LibraryPositions,
    catalogViewModel: CatalogViewModel,
    menu: MenuActions,
    onOpenMenu: () -> Unit,
) {
    // Only this frame draws the rail, so only here is the dot worked out: nothing reads while a
    // title plays.
    val achievementDot: AchievementDotViewModel = hiltViewModel()
    val newAchievement by achievementDot.newAchievement.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalNewAchievement provides newAchievement) {
        saved.SaveableStateProvider(CatalogStateKey) {
            TvCatalogScreen(
                state = catalogState,
                profile = profile,
                fetching = fetching,
                restoreKey = restore.of(here),
                menu = menu,
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
                onOpenSearch = {
                    restore.opened(here, TvSearchEntryKey)
                    at.openSearch()
                },
                onOpenMenu = {
                    restore.forget(here)
                    onOpenMenu()
                },
                onOpenLatest = {
                    restore.opened(here, TvLatestRailKey)
                    at.openLatest()
                },
                onOpenGenresIndex = {
                    restore.opened(here, TvGenresRailKey)
                    at.openGenresIndex()
                },
                onOpenStats = {
                    restore.opened(here, TvStatsRailKey)
                    at.openStats()
                },
                onEntryRestored = { restore.forget(here) },
                onFinish = catalogViewModel::markFinished,
                onOpenGenre = { name ->
                    restore.opened(here, name)
                    at.openGenre(name)
                },
                onOpenFranchise = { id ->
                    restore.opened(here, id.toString())
                    at.openFranchise(id)
                },
                onOpenMoviesPage = {
                    restore.opened(here, TvMoviesPageEntryKey)
                    at.openMoviesPage()
                },
                // The cover story's own "Watch now" — straight to the player,
                // the same as `web/home-cover.js:137`, rather than the title
                // page every other plate on this screen opens.
                onPlay = { setId ->
                    restore.opened(here, setId)
                    at.openPlayer(setId)
                },
                onToggleWatchlist = catalogViewModel::setWatchlisted,
            )
        }
    }
}

/** Where [TvCatalogScreen]'s own saved state — its tab, its wall's scroll — is held while something covers it. */
private const val CatalogStateKey = "catalog"

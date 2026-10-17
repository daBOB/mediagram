package ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.CatalogViewModel
import catalog.MenuScreen
import catalog.fetchResultMessage
import catalog.resolvableQueueRows
import catalog.updateDisabledReason
import designsystem.Spacing
import player.LibraryPlayGate
import player.TitlePreloadViewModel
import stats.AchievementDotViewModel
import system.FetchViewModel
import ui.catalog.FetchResultDialog
import ui.chrome.ProfileBarState
import ui.common.LibraryPositions
import ui.common.MenuActions
import ui.common.catalog.LocalTogetherOffers
import ui.common.catalog.rememberTogetherOffers
import ui.common.rememberLibraryPositions
import ui.player.TogetherNoticePill
import ui.common.settings.SettingsOutcomes
import ui.profile.ProfileGate

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
    val titlePreloadViewModel: TitlePreloadViewModel = hiltViewModel()
    val rawQueueRows by titlePreloadViewModel.queueRows.collectAsStateWithLifecycle(initialValue = emptyList())
    // The rail renders in every frame, root and pushed, so its dot is worked out here, once.
    val achievementDot: AchievementDotViewModel = hiltViewModel()
    val newAchievement by achievementDot.newAchievement.collectAsStateWithLifecycle()
    // Never the raw engine count: a kids profile must not see a grown-up's
    // own preloads counted into its own menu badge.
    val queueCount = remember(rawQueueRows, catalogState) { catalogState.resolvableQueueRows(rawQueueRows).size }
    // The daily limit's door: a kid whose day is used up is not taken into the player.
    val playGate: LibraryPlayGate = hiltViewModel()
    val limitNotice by playGate.notice.collectAsStateWithLifecycle()
    val at = rememberLibraryPositions(playGate)

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
        onPreloads = if (queueCount > 0) at::openPreloads else null,
        preloadCount = queueCount,
    )

    // Watch together's Join, for Home and the title pages to read: each opens
    // the session's title as its own Play does.
    val togetherOffers = rememberTogetherOffers(catalogViewModel, at::openPlayer)
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalTogetherOffers provides togetherOffers) {
            LibraryBranches(at, catalogState, catalogViewModel, fetchState, fetchViewModel, menuActions, profileBar, newAchievement)
        }
        // The pill watch together's notices use, over the page: the viewer stays where they were.
        TogetherNoticePill(limitNotice, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = Spacing.extraLarge * 2))
    }

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

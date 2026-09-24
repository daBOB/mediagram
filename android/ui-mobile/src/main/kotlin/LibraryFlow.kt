package ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.first
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.CatalogViewModel
import system.FetchViewModel

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

    // Counts updates asked for, so each one runs the wait below once.
    // Deliberately not `rememberSaveable`: a request that did not survive
    // the process is a request whose refresh did not either, and waking up
    // to wait for a read nobody started would wait for ever.
    var updatesAsked by remember { mutableIntStateOf(0) }

    LaunchedEffect(updatesAsked) {
        if (updatesAsked == 0) return@LaunchedEffect
        // Both edges, in order, read off the catalog's own flow. The fetch
        // cannot go out beside the refresh: `fetchMissing` works through the
        // catalog as it stands when it is called, so one fired alongside
        // would walk the library this device had before the channel was
        // asked — and the sets it would have filled in are precisely the
        // ones the refresh just brought home.
        //
        // Waiting on the flow rather than on a recomposition is what makes
        // that true. `reload()` only bumps a counter, so the frame after the
        // tap still says the catalog is settled, and an effect that trusted
        // it would fetch immediately — the very race this exists to avoid.
        // The flow emits `refreshing` before it reads anything, so the first
        // wait always has an edge to catch.
        catalogViewModel.state.first(::isReadingChannel)
        catalogViewModel.state.first { !isReadingChannel(it) }
        // Whatever the refresh made of the channel. A read that failed
        // leaves the library this device already had, and its gaps are
        // still gaps worth filling from a provider that has nothing to do
        // with Telegram.
        fetchViewModel.fetch()
    }

    // New media published from another device arrives with no artwork or
    // descriptions here, so the fetch Update library runs follows it too —
    // quietly, since nobody asked. Keyed on nothing: the catalog says when
    // its read has finished, so there is no edge to race the way the button
    // path has to.
    LaunchedEffect(catalogViewModel) {
        catalogViewModel.published.collect { fetchViewModel.fetch(quiet = true) }
    }

    // A fetch lays its artwork down after the shelves were built, and a card
    // looks its poster up when they are; so they are built again to show it.
    LaunchedEffect(fetchViewModel) {
        fetchViewModel.postersArrived.collect { catalogViewModel.showFetched() }
    }

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
            updatesAsked += 1
            catalogViewModel.reload()
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

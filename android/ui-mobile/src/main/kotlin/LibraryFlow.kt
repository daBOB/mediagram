package ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.first
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.CatalogUiState
import catalog.CatalogViewModel
import catalog.collection
import catalog.mediaSet
import model.WatchSnapshot
import system.FetchViewModel

/**
 * The catalog, whichever show or course it opened, whichever title that
 * described, whichever set that played, the system screen, and the TMDB key
 * screen — the first screens here with a real back-stack need. Where those
 * positions are kept, and why, is [LibraryPositions]. Gated on a chosen
 * profile by [ProfileGate], which is what decides whose shelves these are.
 *
 * The library branches below run from the top of the stack down: the player
 * sits over a title, a title over the collection it was opened from, and
 * that over the shelves — so clearing one position falls back to the one it
 * was reached through, which is what makes back from the player land on the
 * description rather than on the catalog. The system and key screens are
 * not positions in that stack; they are one [MenuScreen] laid over whatever
 * is showing, so leaving either uncovers the library screen underneath it
 * and asking for one from the other is a move between them.
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

    val setId = at.setId
    val menuScreen = at.menuScreen
    // Derived from the collected state, so the collection appears of its
    // own accord when the library finishes loading — which is what brings a
    // restored position back to the course it was in. The opened title is
    // resolved the same way and for the same reason.
    val collection = at.collection?.let(catalogState::collection)
    val title = at.titleId?.let(catalogState::mediaSet)
    // Resolved from the collection rather than saved as a tree: a season is
    // one of its show's own divisions, so it only exists once the show it
    // belongs to does, and a stale key from a different show simply fails
    // to find a match here rather than opening the wrong season.
    val season = at.season?.let { name -> collection?.divisions?.find { it.title == name } }
    // Read here rather than at each of the two screens below: both want the
    // same viewer's same snapshot, and neither has another way to reach it —
    // the catalog's own state is the one place it is already collected.
    val watch = (catalogState as? CatalogUiState.Ready)?.watch ?: WatchSnapshot.Empty

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
        onSystem = { at.menuScreen = MenuScreen.System },
        onSettings = { at.menuScreen = MenuScreen.Settings },
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
        onTmdbKey = { at.menuScreen = MenuScreen.TmdbKey },
        onStartOver = onStartOver,
        updateDisabledReason = updateDisabledReason(catalogState, fetchState.running),
        updateNote = if (fetchState.hasKey) null else "Artwork and descriptions need a TMDB key",
    )

    when {
        setId != null -> {
            // The player gets the whole window; a film is the one thing here
            // that wants the space under the system bars.
            BackHandler { at.setId = null }
            PlayerScreen(setId = setId, onBack = { at.setId = null })
        }

        // One branch for both, over the whole enum: a screen the menu
        // opened is left the same way whichever it was, and a second
        // branch here is what let one of them hide the other.
        menuScreen != null -> LibraryBranch(
            destination = menuScreen.destination,
            menu = menuActions,
            profile = profileBar,
            onLeave = { at.menuScreen = null },
        ) {
            when (menuScreen) {
                MenuScreen.System -> SystemScreen()
                MenuScreen.TmdbKey -> TmdbKeyScreen(
                    hasKey = fetchState.hasKey,
                    onSave = fetchViewModel::saveKey,
                )
                // Stands in until the real screen lands.
                MenuScreen.Settings -> SettingsScreen(cache = { CacheBudgetBlock() })
            }
        }

        title != null -> LibraryBranch(
            destination = Destination.Title(title.title),
            menu = menuActions,
            profile = profileBar,
            onLeave = { at.titleId = null },
        ) {
            TitleDetailScreen(
                set = title,
                info = rememberTitleInfo(title.posterKey, catalogViewModel::titleInfo),
                onPlay = { at.setId = title.setId },
            )
        }

        // Checked ahead of the collection itself: a season is a screen the
        // wall opened over it, and back from here has to land on that wall
        // rather than skip past it to the catalog.
        season != null -> LibraryBranch(
            destination = Destination.Season(season.title),
            menu = menuActions,
            profile = profileBar,
            onLeave = { at.season = null },
        ) {
            SeasonScreen(division = season, watch = watch, onOpenTitle = { at.titleId = it })
        }

        collection != null -> LibraryBranch(
            destination = Destination.Collection(collection.name),
            menu = menuActions,
            profile = profileBar,
            // Both cleared together: a season position left behind here
            // would resolve against whichever collection is opened next,
            // and a different show can easily have a division of the same
            // name — "Season 1" is not a fact about one show.
            onLeave = { at.collection = null; at.season = null },
        ) {
            CollectionScreen(
                collection = collection,
                info = rememberTitleInfo(collection.posterKey, catalogViewModel::titleInfo),
                watch = watch,
                posterPath = catalogViewModel::posterPath,
                onOpenTitle = { at.titleId = it },
                onOpenSeason = { at.season = it.title },
            )
        }

        // Also where a saved key lands while the library is still loading,
        // and where one that no longer names anything stays: the shelves are
        // the right thing to show in both cases, and clearing the key here
        // would throw away a position that is about to resolve.
        else -> {
            // onBack is never invoked: LibraryScaffold only wires it up when
            // backLabelFor(Destination.Catalog) says there is a way back,
            // and there is not — the catalog is the top of the tree.
            LibraryScaffold(
                destination = Destination.Catalog,
                onBack = {},
                menu = menuActions,
                profile = profileBar,
            ) {
                CatalogScreen(
                    state = catalogState,
                    fetching = fetchState.running,
                    onOpenTitle = { at.titleId = it },
                    // A leftover season would otherwise resolve against
                    // whichever collection is opened next; see the note on
                    // the collection branch's own onLeave above.
                    onOpenCollection = { at.collection = it; at.season = null },
                )
            }
        }
    }

    // Every branch but the player, which is the one that fills the window
    // with a picture. A fetch started before a film began would otherwise
    // put its tally over the film; the result is held until it is dismissed,
    // so it is still there when the film is left, which is when there is
    // somebody to read it.
    if (setId == null) {
        FetchResultDialog(
            message = fetchResultMessage(fetchState.report, fetchState.error),
            onDismiss = fetchViewModel::dismissResult,
        )
    }
}

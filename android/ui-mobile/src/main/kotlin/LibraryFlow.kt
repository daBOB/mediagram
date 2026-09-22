package ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import system.FetchViewModel
import uniffi.mediagram_core.FetchReport

/**
 * The catalog, whichever show or course it opened, whichever title that
 * described, whichever set that played, the system screen, and the TMDB key
 * screen — the first screens here with a real back-stack need. Where those
 * positions are kept, and why, is [LibraryPositions].
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
internal fun CatalogAndPlayer(onStartOver: () -> Unit) {
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

    val menuActions = MenuActions(
        onSystem = { at.menuScreen = MenuScreen.System },
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
            onLeave = { at.menuScreen = null },
        ) {
            when (menuScreen) {
                MenuScreen.System -> SystemScreen()
                MenuScreen.TmdbKey -> TmdbKeyScreen(
                    hasKey = fetchState.hasKey,
                    onSave = fetchViewModel::saveKey,
                )
            }
        }

        title != null -> LibraryBranch(Destination.Title(title.title), menuActions, { at.titleId = null }) {
            TitleDetailScreen(
                set = title,
                info = rememberShowInfo(title.posterKey, catalogViewModel::showInfo),
                onPlay = { at.setId = title.setId },
            )
        }

        collection != null -> LibraryBranch(
            destination = Destination.Collection(collection.name),
            menu = menuActions,
            onLeave = { at.collection = null },
        ) {
            CollectionScreen(
                collection = collection,
                info = rememberShowInfo(collection.posterKey, catalogViewModel::showInfo),
                onOpenTitle = { at.titleId = it },
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
            ) {
                CatalogScreen(
                    state = catalogState,
                    fetching = fetchState.running,
                    onOpenTitle = { at.titleId = it },
                    onOpenCollection = { at.collection = it },
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

/**
 * Why "Update library" cannot be tapped right now, or `null` when it can.
 *
 * A missing TMDB key is not one of the reasons. It stops the second half
 * and leaves the first worth doing, so the item stays tappable and says
 * what it will skip instead; that is `updateNote`, not this.
 */
private fun updateDisabledReason(state: CatalogUiState, fetching: Boolean): String? = when {
    isReadingChannel(state) -> "Reading the channel…"
    fetching -> "Fetching details and artwork…"
    else -> null
}

/**
 * Whether the channel is being read right now.
 *
 * Read off the catalog's own state rather than a flag beside it, so the two
 * cannot disagree. A read in flight looks like one of two things:
 * [CatalogUiState.Loading] when there were no shelves to keep, and a
 * [CatalogUiState.Ready] that says it is refreshing when there were.
 */
private fun isReadingChannel(state: CatalogUiState): Boolean =
    state is CatalogUiState.Loading || (state is CatalogUiState.Ready && state.refreshing)

/** The sentence a finished or failed fetch leaves behind, or `null` while there is nothing to say. */
private fun fetchResultMessage(report: FetchReport?, error: String?): String? = when {
    error != null -> error
    report != null -> fetchSentence(
        detailsRecorded = report.detailsRecorded.toInt(),
        postersFetched = report.postersFetched.toInt(),
        detailsAlreadyKnown = report.detailsAlreadyKnown.toInt(),
        postersAlreadyHeld = report.postersAlreadyHeld.toInt(),
        noProviderId = report.noProviderId.toInt(),
        failed = report.failed.toInt(),
    )
    else -> null
}

/** What a fetch reported, or what stopped it — shown over whichever library screen is up when it finishes. */
@Composable
private fun FetchResultDialog(message: String?, onDismiss: () -> Unit) {
    if (message == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update library") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

package ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.CatalogUiState
import catalog.CatalogViewModel
import catalog.collection
import catalog.mediaSet
import system.PostersViewModel
import uniffi.mediagram_core.PosterReport

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
    val postersViewModel: PostersViewModel = hiltViewModel()
    val postersState by postersViewModel.state.collectAsStateWithLifecycle()
    val at = rememberLibraryPositions()

    val setId = at.setId
    val menuScreen = at.menuScreen
    // Derived from the collected state, so the collection appears of its
    // own accord when the library finishes loading — which is what brings a
    // restored position back to the course it was in. The opened title is
    // resolved the same way and for the same reason.
    val collection = at.collection?.let(catalogState::collection)
    val title = at.titleId?.let(catalogState::mediaSet)

    val menuActions = MenuActions(
        onSystem = { at.menuScreen = MenuScreen.System },
        // The menu is the same wherever it opens, so this is reachable from
        // the system and key screens, where a reloading catalog is
        // invisible. You asked for the library; the library is what you are
        // shown.
        onRefresh = { at.toCatalog(); catalogViewModel.reload() },
        // To the shelves as well, and for a second reason besides that
        // one: a fetch is minutes of network over hundreds of titles, the
        // menu that started it is already closed, and the shelves are both
        // where the progress line lives and where the artwork lands.
        onFetchPosters = { at.toCatalog(); postersViewModel.fetch() },
        onTmdbKey = { at.menuScreen = MenuScreen.TmdbKey },
        onStartOver = onStartOver,
        refreshDisabledReason = refreshDisabledReason(catalogState),
        fetchPostersDisabledReason = fetchPostersDisabledReason(postersState.running, postersState.hasKey),
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
                    hasKey = postersState.hasKey,
                    onSave = postersViewModel::saveKey,
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
                    fetchingPosters = postersState.running,
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
        PosterFetchResultDialog(
            message = posterFetchResultMessage(postersState.report, postersState.error),
            onDismiss = postersViewModel::dismissResult,
        )
    }
}

/** Why "Fetch posters" cannot be tapped right now, or `null` when it can. */
private fun fetchPostersDisabledReason(running: Boolean, hasKey: Boolean): String? = when {
    running -> "Fetching…"
    !hasKey -> "No TMDB key stored"
    else -> null
}

/**
 * Why "Refresh library" cannot be tapped right now, or `null` when it can.
 *
 * Read off the catalog's own state rather than a flag beside it, so the two
 * cannot disagree. A read of the channel in flight looks like one of two
 * things: [CatalogUiState.Loading] when there were no shelves to keep, and
 * a [CatalogUiState.Ready] that says it is refreshing when there were.
 */
private fun refreshDisabledReason(state: CatalogUiState): String? = when {
    state is CatalogUiState.Loading -> "Refreshing…"
    state is CatalogUiState.Ready && state.refreshing -> "Refreshing…"
    else -> null
}

/** The sentence a finished or failed fetch leaves behind, or `null` while there is nothing to say. */
private fun posterFetchResultMessage(report: PosterReport?, error: String?): String? = when {
    error != null -> error
    report != null -> posterReportLine(
        fetched = report.fetched.toInt(),
        alreadyHeld = report.alreadyHeld.toInt(),
        noProviderId = report.noProviderId.toInt(),
        failed = report.failed.toInt(),
    )
    else -> null
}

/** What a fetch reported, or what stopped it — shown over whichever library screen is up when it finishes. */
@Composable
private fun PosterFetchResultDialog(message: String?, onDismiss: () -> Unit) {
    if (message == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fetch posters") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

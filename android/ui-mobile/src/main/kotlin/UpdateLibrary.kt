package ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import catalog.CatalogUiState
import uniffi.mediagram_core.FetchReport

/**
 * Why "Update library" cannot be tapped right now, or `null` when it can.
 *
 * A missing TMDB key is not one of the reasons. It stops the second half
 * and leaves the first worth doing, so the item stays tappable and says
 * what it will skip instead; that is `updateNote`, not this.
 */
internal fun updateDisabledReason(state: CatalogUiState, fetching: Boolean): String? = when {
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
internal fun isReadingChannel(state: CatalogUiState): Boolean =
    state is CatalogUiState.Loading || (state is CatalogUiState.Ready && state.refreshing)

/** The sentence a finished or failed fetch leaves behind, or `null` while there is nothing to say. */
internal fun fetchResultMessage(report: FetchReport?, error: String?): String? = when {
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
internal fun FetchResultDialog(message: String?, onDismiss: () -> Unit) {
    if (message == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update library") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

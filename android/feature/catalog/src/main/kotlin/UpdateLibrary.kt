package catalog

import uniffi.mediagram_core.FetchReport

/**
 * Why "Update library" cannot be tapped right now, or `null` when it can.
 *
 * A missing TMDB key is not one of the reasons. It stops the second half
 * and leaves the first worth doing, so the item stays tappable and says
 * what it will skip instead; that is `updateNote`, not this.
 */
fun updateDisabledReason(
    state: CatalogUiState,
    fetching: Boolean,
): String? =
    when {
        isReadingChannel(state) -> "Reading the channel…"
        fetching -> "Fetching details and artwork…"
        else -> null
    }

/**
 * Whether the channel is being read right now.
 *
 * The coordinator supplies the refresh flag independently of shelf regrouping.
 * This predicate only controls presentation. A read in flight looks like
 * one of two things:
 * [CatalogUiState.Loading] when there were no shelves to keep, and a
 * [CatalogUiState.Ready] that says it is refreshing when there were.
 */
fun isReadingChannel(state: CatalogUiState): Boolean =
    state is CatalogUiState.Loading || (state is CatalogUiState.Ready && state.refreshing)

/** The sentence a finished or failed fetch leaves behind, or `null` while there is nothing to say. */
fun fetchResultMessage(
    report: FetchReport?,
    error: String?,
): String? =
    when {
        error != null -> {
            error
        }

        report != null -> {
            fetchSentence(
                detailsRecorded = report.detailsRecorded.toInt(),
                postersFetched = report.postersFetched.toInt(),
                detailsAlreadyKnown = report.detailsAlreadyKnown.toInt(),
                postersAlreadyHeld = report.postersAlreadyHeld.toInt(),
                noProviderId = report.noProviderId.toInt(),
                failed = report.failed.toInt(),
            )
        }

        else -> {
            null
        }
    }

package catalog

import data.CatalogEnrichmentState
import uniffi.mediagram_core.FetchProgress
import uniffi.mediagram_core.FetchReport
import uniffi.mediagram_core.FetchStep
import kotlin.math.ceil

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
    progress: FetchProgress? = null,
): String? =
    when {
        isReadingChannel(state) -> "Reading the channel…"
        fetching -> progress?.let(::fetchProgressSentence) ?: "Fetching details and artwork…"
        else -> null
    }

/** What "Update library" says beneath itself: what it will skip, or where the artwork comes from instead of a TMDB key. */
fun updateNote(fetch: CatalogEnrichmentState): String? =
    when {
        fetch.hasKey -> null
        fetch.viaHomeCache -> "Artwork comes from the home cache server."
        else -> "Artwork and descriptions need a TMDB key"
    }

/** "Looking up titles · 212 of 640 · about 3 min left": the step, its count, and what its pace says is left. */
fun fetchProgressSentence(progress: FetchProgress): String {
    val step =
        when (progress.step) {
            FetchStep.LOOKING_UP -> "Looking up titles"
            FetchStep.DOWNLOADING -> "Downloading artwork"
            FetchStep.DESCRIBING -> "Saving descriptions"
        }
    return listOfNotNull(step, "${progress.done} of ${progress.total}", fetchTimeLeft(progress)).joinToString(" · ")
}

/** How far the step is through its items, for a bar; `null` for a step with none. */
fun fetchFraction(progress: FetchProgress): Float? =
    if (progress.total == 0u) null else progress.done.toFloat() / progress.total.toFloat()

/**
 * What the step's own pace says is left, or `null` until it has a pace: the
 * first items carry the connection's setup, and an estimate that opens at
 * an hour and drops to minutes reads as broken. Each step has its own pace —
 * a lookup is a request, a download an image — so none borrows another's.
 */
internal fun fetchTimeLeft(progress: FetchProgress): String? {
    val done = progress.done.toLong()
    val elapsedMs = progress.stepElapsedMs.toLong()
    if (done < MIN_ITEMS_FOR_ESTIMATE || elapsedMs < MIN_MS_FOR_ESTIMATE) return null
    val leftMs = elapsedMs * (progress.total.toLong() - done) / done
    val minutes = ceil(leftMs / MS_PER_MINUTE).toLong()
    return if (minutes <= 1) "less than a minute left" else "about $minutes min left"
}

private const val MIN_ITEMS_FOR_ESTIMATE = 5L
private const val MIN_MS_FOR_ESTIMATE = 3_000L
private const val MS_PER_MINUTE = 60_000.0

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

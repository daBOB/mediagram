package system

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import data.CoreProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import playback.PlaybackCounters
import javax.inject.Inject

/**
 * Reads the catalog's own facts and the byte path's counters, once per
 * subscription rather than once per instance: the System screen is a
 * snapshot a viewer opens to check on, not a live dashboard, and a viewer
 * who leaves to play a title and comes back expects the numbers to have
 * moved. [SharingStarted.WhileSubscribed] drops the upstream five seconds
 * after the screen is left and re-runs it on the next visit — the same
 * shape [catalog.CatalogViewModel] refreshes the shelves by.
 *
 * [CoreProvider], not [data.CoreClient] directly: there is a window
 * between signing a device out and setting it up again in which no core
 * exists, so it is reached the way every other screen reaches it — through
 * [CoreProvider.awaitCore], never captured.
 */
@HiltViewModel
class SystemViewModel @Inject constructor(
    private val coreProvider: CoreProvider,
    private val counters: PlaybackCounters,
) : ViewModel() {

    val state: StateFlow<SystemUiState?> = flow {
        val core = coreProvider.awaitCore()
        val facts = core.catalogFacts()
        val totals = counters.totals()
        emit(
            SystemUiState(
                origin = facts.origin,
                sets = facts.sets.toLong(),
                posters = facts.posters.toLong(),
                schema = facts.schema.toInt(),
                fromCacheBytes = totals.fromCacheBytes,
                fromUpstreamBytes = totals.fromUpstreamBytes,
                fetches = totals.fetches,
                failedReads = totals.failedReads,
                // A live session only means something once the catalog is
                // bound to a channel; a published package has no
                // connection for this row to report on.
                connected = if (facts.origin == "channel") core.isAuthorized() else null,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

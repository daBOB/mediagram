package system

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import data.CoreProvider
import data.RefreshLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import playback.CacheProvider
import playback.PlaybackCounters
import javax.inject.Inject

/**
 * Reads the catalog's own facts, the disk cache's own occupancy, and the
 * byte path's counters, once per subscription rather than once per
 * instance: the System screen is a snapshot a viewer opens to check on,
 * not a live dashboard, and a viewer who leaves to play a title and comes
 * back expects the numbers to have moved. [SharingStarted.WhileSubscribed]
 * drops the upstream five seconds after the screen is left and re-runs it
 * on the next visit — the same shape [catalog.CatalogViewModel] refreshes
 * the shelves by.
 *
 * [CoreProvider], not [data.CoreClient] directly: there is a window
 * between signing a device out and setting it up again in which no core
 * exists, so it is reached the way every other screen reaches it — through
 * [CoreProvider.awaitCore], never captured.
 */
@HiltViewModel
class SystemViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val coreProvider: CoreProvider,
        private val counters: PlaybackCounters,
        private val refreshes: RefreshLog,
    ) : ViewModel() {
        // Read once, not per subscription: the installed package's own version
        // name cannot change while this process is running.
        private val versionName: String? =
            try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }

        private val requests = MutableStateFlow(0)
        private val _failure = MutableStateFlow<String?>(null)
        val failure: StateFlow<String?> = _failure.asStateFlow()
        private var lastSnapshot: SystemUiState? = null

        val state: StateFlow<SystemUiState?> =
            requests
                .map {
                    // Catch each read inside the retry flow: a catch after stateIn,
                    // or one that ends this flow, would leave no collector to retry.
                    try {
                        snapshot().also {
                            lastSnapshot = it
                            _failure.value = null
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (
                        @Suppress("TooGenericExceptionCaught") e: Exception,
                    ) {
                        _failure.value = "System information could not be read. Try again."
                        lastSnapshot
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        /** Re-reads on the retained subscription, including after repeated failures. */
        fun retry() {
            requests.update { it + 1 }
        }

        private suspend fun snapshot(): SystemUiState {
            val core = coreProvider.awaitCore()
            val facts = core.catalogFacts()
            val totals = counters.totals()
            val occupancy = CacheProvider.occupancy(context)
            return SystemUiState(
                origin = facts.origin,
                sets = facts.sets.toLong(),
                posters = facts.posters.toLong(),
                schema = facts.schema.toInt(),
                // Seconds at the core's surface, milliseconds here: the
                // row subtracts it from a wall clock.
                publishedAt = facts.publishedAt?.times(1_000),
                lastRefresh = refreshes.last(),
                heldBytes = occupancy.heldBytes,
                budgetBytes = occupancy.budgetBytes,
                fromCacheBytes = totals.fromCacheBytes,
                fromUpstreamBytes = totals.fromUpstreamBytes,
                fetches = totals.fetches,
                failedReads = totals.failedReads,
                // A live session only means something once the catalog is
                // bound to a channel; a published package has no
                // connection for this row to report on.
                connected = if (facts.origin == "channel") core.isAuthorized() else null,
                versionName = versionName,
                // Process start, not ViewModel construction: a viewer who
                // reopens this screen after playing for an hour should read
                // an hour, not however long the screen itself has existed.
                uptimeSeconds = (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()) / 1000,
            )
        }
    }

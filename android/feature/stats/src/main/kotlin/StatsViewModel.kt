package stats

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import data.CoreProvider
import data.WatchStateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.ZonedDateTime
import javax.inject.Inject

/**
 * The chosen profile's stats, read once per visit the way the System
 * screen is: the read stops five seconds after the page is left and runs
 * again on the next visit, so minutes watched in between are there when
 * the viewer comes back.
 *
 * Only ever the chosen profile's. [flatMapLatest] cancels a read for a
 * profile that was left, so it is never published; resetting the shared
 * state once the page is left means whoever opens it next never sees the
 * previous profile's page, not even for a frame.
 *
 * Sets are named later, from the profile's own catalogue (`statsUiStateOf`),
 * which lives in another feature module.
 */
@HiltViewModel
class StatsViewModel
    @Inject
    constructor(
        private val coreProvider: CoreProvider,
        private val watchState: WatchStateRepository,
        private val seen: AchievementsSeen,
    ) : ViewModel() {
        /** This device's clock and zone: today's date for the read, and the clock every line's time is told on. */
        internal var now: () -> ZonedDateTime = { ZonedDateTime.now() }

        /** The profile the page last read, and the ids it was shown — what [markAchievementsSeen] records. */
        private var shown: Pair<String, Set<String>>? = null

        /**
         * What the page shows now becomes what this device has shown: the
         * rail's dot goes out. Called by the page while it is on screen —
         * not by the read, whose shared flow outlives the page by a few
         * seconds.
         */
        fun markAchievementsSeen() {
            shown?.let { (profileId, ids) -> seen.markSeen(profileId, ids) }
        }

        val state: StateFlow<StatsRead> =
            watchState.chosenProfileId
                .flatMapLatest { id -> if (id == null) flowOf(StatsRead.Loading) else readOf(id) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0), StatsRead.Loading)

        private fun readOf(profileId: String): Flow<StatsRead> =
            flow {
                // Until this read lands the page shows nothing to mark — least of all the last profile's.
                shown = null
                emit(StatsRead.Loading)
                val read =
                    try {
                        val at = now()
                        val today = at.toLocalDate().toString()
                        val core = coreProvider.awaitCore()
                        val summary = core.stats(profileId, today)
                        val achievements = core.achievements(profileId, today, utcOffsetMinutes(at))
                        shown = profileId to achievements.earned.mapTo(HashSet()) { it.id }
                        StatsRead.Done(summary, at, achievements)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (
                        @Suppress("TooGenericExceptionCaught") e: Exception,
                    ) {
                        // stats() itself never throws (a storage failure answers an
                        // empty summary); this is the core not being reachable at all.
                        Log.w(TAG, "stats: ${e.message}")
                        StatsRead.Failed(e.message)
                    }
                emit(read)
            }

        private companion object {
            const val TAG = "stats"
        }
    }

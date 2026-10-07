package stats

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import data.CoreProvider
import data.WatchStateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.ZonedDateTime
import javax.inject.Inject

/**
 * How long the dot waits after a watch-state change before reading again —
 * longer than the player's ten-second save tick, so a title playing under
 * the phone's still-composed rail is not read on every position it saves.
 * The web's dot keeps the same rhythm (`stats-dot.js`).
 */
internal const val DOT_SETTLE_MS = 15_000L

/**
 * Whether the rail's Stats row wears the new-achievement dot: something the
 * chosen profile has earned that this device has not shown it yet.
 *
 * Read at once when a profile is chosen — the last profile's dot goes out
 * before the answer arrives — and again once the profile's watch state has
 * been quiet for [DOT_SETTLE_MS] after a change: a write here, or another
 * device's rows pulled in by a sync round, which is how an achievement
 * earned on the television lights the tablet's dot. Never a pop-up: the dot
 * is all of it.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class AchievementDotViewModel
    @Inject
    constructor(
        private val coreProvider: CoreProvider,
        watchState: WatchStateRepository,
        seen: AchievementsSeen,
    ) : ViewModel() {
        /** This device's clock and zone: the day and offset the core places a day-based achievement by. */
        internal var now: () -> ZonedDateTime = { ZonedDateTime.now() }

        /** The last answer: the profile it was for and what it had earned. Outlives the reads, which stop while the rail is away. */
        private var answered: Pair<String, Set<String>>? = null

        /** The chosen profile and what it has earned; `null` while there is no answer for it yet. */
        private val earned: Flow<Pair<String, Set<String>>?> =
            watchState.chosenProfileId.flatMapLatest { id ->
                if (id == null) {
                    flowOf(null)
                } else {
                    flow {
                        // The rail back for the same profile keeps its dot until the new read lands;
                        // a different profile's goes out at once.
                        emit(answered?.takeIf { it.first == id })
                        emitRead(id)
                        // The snapshot as it stands was just read; only what changes after it counts.
                        watchState.snapshot.drop(1).debounce(DOT_SETTLE_MS).collect { emitRead(id) }
                    }
                }
            }

        val newAchievement: StateFlow<Boolean> =
            combine(earned, seen.seen) { found, shown ->
                found != null && !shown[found.first].orEmpty().containsAll(found.second)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        /** A read that failed says nothing new, so the dot stays as it was — as the web's does. */
        private suspend fun FlowCollector<Pair<String, Set<String>>?>.emitRead(profileId: String) {
            val ids = earnedIds(profileId) ?: return
            answered = profileId to ids
            emit(answered)
        }

        private suspend fun earnedIds(profileId: String): Set<String>? {
            val at = now()
            return try {
                coreProvider
                    .awaitCore()
                    .achievements(profileId, at.toLocalDate().toString(), utcOffsetMinutes(at))
                    .earned
                    .mapTo(HashSet()) { it.id }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                Log.w(TAG, "achievements read failed", e)
                null
            }
        }

        private companion object {
            const val TAG = "stats"
        }
    }

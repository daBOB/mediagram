package system

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import playback.CacheOccupancy
import playback.CacheProvider
import playback.CacheVolume
import playback.CacheVolumeSettings
import playback.PlainCacheVolumeSettings
import playback.budgetLadder
import playback.cacheVolumes
import javax.inject.Inject

/**
 * The sizes offered for the cache, smallest first: the floor, doubling up
 * to [cap]. A picker rather than a free number, because the useful sizes
 * are few and a typed one would need its own validation for no gain.
 */
fun cacheBudgetChoices(cap: Long): List<Long> = budgetLadder(cap)

/**
 * The cache half of Settings: how much is held against how much is allowed
 * and where it lives, and changing either. The allowance applies at once,
 * evicting down to a smaller one rather than waiting for the next film to
 * make room; the volume takes effect the next time the app starts.
 */
@HiltViewModel
class CacheBudgetViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val dispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val volumeSettings: CacheVolumeSettings = PlainCacheVolumeSettings(context)

        private val _state = MutableStateFlow<CacheOccupancy?>(null)
        val state: StateFlow<CacheOccupancy?> = _state.asStateFlow()
        private val _failure = MutableStateFlow<String?>(null)
        val failure: StateFlow<String?> = _failure.asStateFlow()

        private val _volumes = MutableStateFlow<List<CacheVolume>>(emptyList())
        val volumes: StateFlow<List<CacheVolume>> = _volumes.asStateFlow()
        private val _chosenVolumeId = MutableStateFlow<String?>(null)
        val chosenVolumeId: StateFlow<String?> = _chosenVolumeId.asStateFlow()

        // One change at a time: two quick taps finishing out of order would leave
        // the live budget and the saved one disagreeing.
        private val changing = Mutex()

        /**
         * Reads what is held now, and what is on offer for where it lives.
         * Called whenever Settings opens; this outlives it.
         *
         * `cacheVolumes(context)` walks `StorageManager` and stats every
         * candidate volume, and `volumeSettings.read()` is a prefs read —
         * neither belongs on `viewModelScope`'s main dispatcher, so both run
         * on [dispatcher] alongside [CacheProvider.occupancy]'s own I/O.
         */
        fun refresh() {
            viewModelScope.launch {
                guarded("Could not read the cache. Try again.") {
                    val occupancy = CacheProvider.occupancy(context)
                    val (availableVolumes, chosenId) =
                        withContext(dispatcher) { cacheVolumes(context) to volumeSettings.read() }
                    _state.value = occupancy
                    _volumes.value = availableVolumes
                    _chosenVolumeId.value = chosenId
                }
            }
        }

        fun choose(bytes: Long) {
            viewModelScope.launch {
                guarded("Could not confirm the cache allowance. Try again.") {
                    CacheProvider.setBudget(context, bytes)
                    _state.value = CacheProvider.occupancy(context)
                }
            }
        }

        /** Persists which volume the cache should open on next; it does not move the live cache. */
        fun chooseVolume(id: String) {
            viewModelScope.launch {
                guarded("Could not save the cache location. Try again.") {
                    volumeSettings.write(id)
                    _chosenVolumeId.value = id
                }
            }
        }

        /** Keep the last confirmed occupancy until a read succeeds, with a retryable notice on failure. */
        private suspend fun guarded(
            sentence: String,
            work: suspend () -> Unit,
        ) = changing.withLock {
            try {
                work()
                _failure.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                Log.w("CacheBudget", "the cache could not be read or resized", e)
                _failure.value = sentence
            }
        }
    }

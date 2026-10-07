package player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import model.MediaSet
import playback.HeldSetsQuery
import playback.SeriesPreloading

/**
 * Whether the open title plays with no network at all — the player's own
 * "cached" readout, matching the web's `preloadReadout`. Split out of
 * [PlayerViewModel] to keep that file under the project's line guideline.
 *
 * Driven by [openSet] rather than by explicit start/stop calls: a rescan
 * runs whenever the resolved title changes (including back to `null`, on
 * [PlayerViewModel.stop]), and [SeriesPreloader.heldEvents] flips it to
 * `true` the moment a running preload finishes the very title now open,
 * without waiting for the next rescan.
 */
class PlayerHeldController(
    scope: CoroutineScope,
    private val heldSets: HeldSetsQuery,
    seriesPreloader: SeriesPreloading,
    openSet: StateFlow<MediaSet?>,
) {
    private val _held = MutableStateFlow(false)
    val held: StateFlow<Boolean> = _held.asStateFlow()

    init {
        scope.launch {
            openSet.collect { set ->
                _held.value = if (set != null) heldSets.isHeld(set.setId, set.totalBytes) else false
            }
        }
        scope.launch {
            seriesPreloader.heldEvents.collect { setId ->
                if (setId == openSet.value?.setId) _held.value = true
            }
        }
    }
}

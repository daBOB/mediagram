package system

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import playback.CACHE_MAX_BYTES
import playback.CacheOccupancy
import playback.CacheProvider
import playback.MIN_CACHE_BYTES
import javax.inject.Inject

private const val GIB = 1L shl 30

/**
 * The sizes offered for the cache, smallest first: the floor, then doubling
 * past the default. A picker rather than a free number, because the useful
 * sizes are few and a typed one would need its own validation for no gain.
 */
fun cacheBudgetChoices(): List<Long> =
    (listOf(MIN_CACHE_BYTES, GIB, CACHE_MAX_BYTES, 4 * GIB, 8 * GIB)).distinct().sorted()

/**
 * The cache half of Settings: how much is held against how much is allowed,
 * and changing the allowance — which applies at once, evicting down to a
 * smaller one rather than waiting for the next film to make room.
 */
@HiltViewModel
class CacheBudgetViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow<CacheOccupancy?>(null)
    val state: StateFlow<CacheOccupancy?> = _state.asStateFlow()

    init {
        viewModelScope.launch { _state.value = CacheProvider.occupancy(context) }
    }

    fun choose(bytes: Long) {
        viewModelScope.launch {
            CacheProvider.setBudget(context, bytes)
            _state.value = CacheProvider.occupancy(context)
        }
    }
}

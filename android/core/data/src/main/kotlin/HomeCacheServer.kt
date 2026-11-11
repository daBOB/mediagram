package data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the core has a home cache server to read from, as last set by the
 * link that tells it (`playback.linkHomeCache`). The enrichment fetch reads
 * this rather than discovery itself, so the fetch and the core always agree:
 * with no TMDB key stored, a fetch reads what the server holds only while the
 * core has one, and away from home, or with the LAN cache switched off, it
 * stops as it always has.
 */
class HomeCacheServer {
    private val linkedNow = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = linkedNow.asStateFlow()

    fun linked(connected: Boolean) {
        linkedNow.value = connected
    }
}

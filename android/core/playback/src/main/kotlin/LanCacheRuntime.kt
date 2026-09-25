package playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the last LAN write was rejected for a bad token — a fact
 * [LanWriteQueue] surfaces through its `onUnauthorized` callback and
 * Settings reads back to show "Pairing token rejected". Cleared when a
 * viewer saves a new token, which is worth trying again.
 */
class LanCacheTokenStatus {
    private val _rejected = MutableStateFlow(false)
    val rejected: StateFlow<Boolean> = _rejected.asStateFlow()

    fun markRejected() {
        _rejected.value = true
    }

    fun clear() {
        _rejected.value = false
    }
}

/**
 * Everything a chunk source needs to try the LAN before Telegram, bundled
 * once so the player and the preloader — both read through
 * [MlibDataSourceFactory] — share one discovery, one write queue and one
 * set of settings rather than each standing up its own.
 */
class LanCacheRuntime(
    val client: LanChunkProtocol,
    val locator: LanServerLocator,
    val settings: LanCacheSettings,
    val tokenStatus: LanCacheTokenStatus,
    val network: UnmeteredNetworkCheck,
    val writes: LanChunkWriter,
) {
    /** `null` when the feature is off, unpaired, or no server has verified yet — [LanFirstChunkSource] reads Telegram then. */
    suspend fun server(): LanServer? = if (settings.enabled()) locator.server.value else null
}

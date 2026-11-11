package playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Whether the last LAN write was rejected for a bad token — a fact
 * [LanWriteQueue] surfaces through its `onUnauthorized` callback and
 * Settings reads back to show "Pairing token rejected". Cleared when a
 * viewer saves a new token, which is worth trying again.
 */
class LanCacheTokenStatus {
    private val _rejected = MutableStateFlow(false)
    val rejected: StateFlow<Boolean> = _rejected.asStateFlow()

    private val _settingsChanges = MutableStateFlow(0)

    /**
     * Counts saved changes to the LAN cache's switch or token, which have no
     * change signal of their own, so whatever acts on them reads them again.
     */
    val settingsChanges: StateFlow<Int> = _settingsChanges.asStateFlow()

    fun markRejected() {
        _rejected.value = true
    }

    fun clear() {
        _rejected.value = false
    }

    fun settingsChanged() {
        _settingsChanges.update { it + 1 }
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

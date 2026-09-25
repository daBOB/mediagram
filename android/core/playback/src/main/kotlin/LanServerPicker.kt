package playback

import kotlinx.coroutines.flow.StateFlow

/** The mDNS service type `mediagram_cache` advertises — `crates/mediagram-cache/src/mdns.rs`'s `SERVICE_TYPE`. */
const val LAN_CACHE_SERVICE_TYPE = "_mediagram-cache._tcp"

/** Verifies one candidate base URL actually answers as a LAN cache server. */
fun interface LanServerProbe {
    suspend fun verify(baseUrl: String): Boolean
}

/**
 * Picks a server to use: [manualOverride] always wins when it verifies,
 * regardless of where discovery placed it among [discovered]; otherwise the
 * first of [discovered] to answer `GET /v1/status`. `null` when nothing
 * verifies — an OEM's NSD stack going quiet and a server that has simply
 * stopped look the same from here, and both mean "use Telegram".
 *
 * Pure apart from [probe] itself, and the one piece of [LanServerLocator] a
 * test can drive without a real `NsdManager`.
 */
suspend fun pickLanServer(
    discovered: List<LanServer>,
    manualOverride: String?,
    probe: LanServerProbe,
): LanServer? {
    if (manualOverride != null && probe.verify(manualOverride)) {
        return LanServer(manualOverride, hostOf(manualOverride))
    }
    return discovered.firstOrNull { probe.verify(it.baseUrl) }
}

private fun hostOf(baseUrl: String): String = baseUrl.removePrefix("http://").removePrefix("https://").trimEnd('/')

/** What a Settings view model and [LanCacheRuntime] actually need from discovery — narrow enough for a test to fake without a real `NsdManager`. */
interface LanServerSource {
    val server: StateFlow<LanServer?>
    val searching: StateFlow<Boolean>

    fun discover()
}

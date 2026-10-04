package playback

import kotlinx.coroutines.flow.StateFlow
import java.net.URL

/** The mDNS service type `mediagram_cache` advertises — `crates/mediagram-cache/src/mdns.rs`'s `SERVICE_TYPE`. */
const val LAN_CACHE_SERVICE_TYPE = "_mediagram-cache._tcp"

/** The port `mediagram_cache` listens on unless told otherwise — `crates/mediagram-cache/src/config.rs`'s `DEFAULT_LISTEN`. */
const val LAN_CACHE_DEFAULT_PORT = 7788

/**
 * A manual address typed as just a host ("http://192.168.0.240") means the
 * server's own default port, not HTTP's 80: another web server on the same
 * host answers there, and the cache would read as "Not found". Applied when
 * probing rather than when saving, so an address saved without a port works too.
 */
internal fun withDefaultPort(address: String): String {
    val url = runCatching { URL(address) }.getOrNull() ?: return address
    if (url.port != -1) return address
    return URL(url.protocol, url.host, LAN_CACHE_DEFAULT_PORT, url.file).toString().trimEnd('/')
}

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
    if (manualOverride != null) {
        val address = withDefaultPort(manualOverride)
        if (probe.verify(address)) return LanServer(address, hostOf(address))
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

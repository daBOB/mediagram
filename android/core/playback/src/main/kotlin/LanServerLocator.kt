package playback

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

/**
 * The impure half: `NsdManager` discovery feeding [pickLanServer]. Not unit
 * tested directly — a multicast socket needs a real network stack, the same
 * reason `crates/mediagram-cache/src/mdns.rs` carries none either — proven
 * by hand on the Redmi instead (phase's device step).
 *
 * [discover] runs one pass at a time: Settings opening, app start, and a
 * network change, never on a timer — a phone that never stops listening for
 * multicast is a phone that never sleeps its radio.
 *
 * `NsdManager.resolveService` allows only one outstanding call at a time on
 * API < 34; [queueResolve] serialises every `onServiceFound` through one
 * pending queue rather than firing them all at once.
 */
class LanServerLocator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val client: LanChunkProtocol,
    private val manualAddress: suspend () -> String?,
) : LanServerSource {
    private val _server = MutableStateFlow<LanServer?>(null)
    override val server: StateFlow<LanServer?> = _server.asStateFlow()

    /** `true` from [discover] until the first pick pass finishes — Settings' "Searching" row. */
    private val _searching = MutableStateFlow(false)
    override val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val nsdManager by lazy { context.getSystemService(Context.NSD_SERVICE) as NsdManager }
    private val wifiManager by lazy { context.getSystemService(Context.WIFI_SERVICE) as WifiManager }
    private var multicastLock: WifiManager.MulticastLock? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val found = mutableListOf<LanServer>()
    private val pendingResolves = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    /** Starts one discovery pass, trying the manual override immediately rather than waiting on mDNS to catch up. */
    override fun discover() {
        stopDiscovery()
        found.clear()
        _searching.value = true
        multicastLock = wifiManager.createMulticastLock("mediagram-cache-discovery").apply { acquire() }
        val listener =
            object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) = Unit

                override fun onServiceFound(serviceInfo: NsdServiceInfo) = queueResolve(serviceInfo)

                override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

                override fun onDiscoveryStopped(serviceType: String) = Unit

                override fun onStartDiscoveryFailed(
                    serviceType: String,
                    errorCode: Int,
                ) = Unit

                override fun onStopDiscoveryFailed(
                    serviceType: String,
                    errorCode: Int,
                ) = Unit
            }
        discoveryListener = listener
        nsdManager.discoverServices(LAN_CACHE_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        pick()
    }

    fun stopDiscovery() {
        discoveryListener?.let { runCatching { nsdManager.stopServiceDiscovery(it) } }
        discoveryListener = null
        multicastLock?.let { if (it.isHeld) it.release() }
        multicastLock = null
    }

    private fun queueResolve(serviceInfo: NsdServiceInfo) {
        pendingResolves.addLast(serviceInfo)
        if (!resolving) resolveNext()
    }

    private fun resolveNext() {
        val next = pendingResolves.removeFirstOrNull()
        if (next == null) {
            resolving = false
            return
        }
        resolving = true
        nsdManager.resolveService(
            next,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(
                    serviceInfo: NsdServiceInfo,
                    errorCode: Int,
                ) = resolveNext()

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    serviceInfo.host?.hostAddress?.let { host ->
                        found.add(LanServer("http://$host:${serviceInfo.port}", "$host:${serviceInfo.port}"))
                        pick()
                    }
                    resolveNext()
                }
            },
        )
    }

    private fun pick() {
        scope.launch {
            _server.value = pickLanServer(found.toList(), manualAddress(), LanServerProbe { client.verify(it) })
            _searching.value = false
        }
    }
}

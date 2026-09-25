package playback

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.Inet4Address

/**
 * The impure half: `NsdManager` discovery feeding [pickLanServer]. Not unit
 * tested directly — a multicast socket needs a real network stack, the same
 * reason `crates/mediagram-cache/src/mdns.rs` carries none either.
 *
 * [discover] runs one bounded pass — [passWindowMs], or sooner if a
 * verified server turns up first — never a continuous scan; that and the
 * [multicastLock] held only for the pass's duration are what keep this
 * from holding radio open for the process's whole lifetime. Every entry
 * point (`NsdManager`'s own callbacks run on an arbitrary binder thread,
 * not [scope]'s) re-enters through `scope.launch`, so [found], [_server]
 * and [_searching] are only ever touched from [scope]'s own dispatcher —
 * this assumes that dispatcher is confined (single-threaded, as
 * `Dispatchers.Main.immediate` is), which is what makes a `Mutex`
 * unnecessary. A monotonic [generation] discards any callback still
 * arriving from a pass [discover] has since superseded.
 */
class LanServerLocator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val client: LanChunkProtocol,
    private val passWindowMs: Long = PASS_WINDOW_MS,
    private val manualAddress: suspend () -> String?,
) : LanServerSource {
    private val _server = MutableStateFlow<LanServer?>(null)
    override val server: StateFlow<LanServer?> = _server.asStateFlow()

    /** `true` from [discover] until the pass ends — Settings' "Searching" row. */
    private val _searching = MutableStateFlow(false)
    override val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val nsdManager by lazy { context.getSystemService(Context.NSD_SERVICE) as NsdManager }
    private val wifiManager by lazy { context.getSystemService(Context.WIFI_SERVICE) as WifiManager }
    private var multicastLock: WifiManager.MulticastLock? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var timeoutJob: Job? = null
    private val found = mutableListOf<LanServer>()
    private val pendingResolves = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var generation = 0

    private val networkWatcher =
        LanServerNetworkWatcher(
            context = context,
            onLost = { scope.launch { _server.value = null } },
            onUnmeteredAvailable = { scope.launch { discover() } },
        )

    init {
        networkWatcher.start()
    }

    /** Starts one discovery pass, trying the manual override immediately rather than waiting on mDNS to catch up. */
    override fun discover() {
        val myGeneration = ++generation
        timeoutJob?.cancel()
        stopDiscovery()
        found.clear()
        _searching.value = true
        multicastLock = wifiManager.createMulticastLock("mediagram-cache-discovery").apply { acquire() }
        val listener =
            object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) = Unit

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    scope.launch { if (myGeneration == generation) queueResolve(serviceInfo) }
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

                override fun onDiscoveryStopped(serviceType: String) = Unit

                override fun onStartDiscoveryFailed(
                    serviceType: String,
                    errorCode: Int,
                ) {
                    scope.launch { endPass(myGeneration) }
                }

                override fun onStopDiscoveryFailed(
                    serviceType: String,
                    errorCode: Int,
                ) = Unit
            }
        discoveryListener = listener
        runCatching { nsdManager.discoverServices(LAN_CACHE_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { scope.launch { endPass(myGeneration) } }
        timeoutJob =
            scope.launch {
                delay(passWindowMs)
                endPass(myGeneration)
            }
        pick(myGeneration)
    }

    private fun stopDiscovery() {
        discoveryListener?.let { runCatching { nsdManager.stopServiceDiscovery(it) } }
        discoveryListener = null
        multicastLock?.let { if (it.isHeld) it.release() }
        multicastLock = null
    }

    /** Ends the pass identified by [generationAtCall] — a stale pass's own timeout or failure must never end a newer one. */
    private fun endPass(generationAtCall: Int) {
        if (generationAtCall != generation) return
        timeoutJob?.cancel()
        timeoutJob = null
        stopDiscovery()
        _searching.value = false
    }

    private fun queueResolve(serviceInfo: NsdServiceInfo) {
        pendingResolves.addLast(serviceInfo)
        if (!resolving) resolveNext()
    }

    /** `NsdManager.resolveService` allows only one outstanding call at a time on API < 34 — this is what serialises them. */
    private fun resolveNext() {
        val next = pendingResolves.removeFirstOrNull()
        if (next == null) {
            resolving = false
            return
        }
        resolving = true
        val myGeneration = generation
        nsdManager.resolveService(
            next,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(
                    serviceInfo: NsdServiceInfo,
                    errorCode: Int,
                ) {
                    scope.launch { if (myGeneration == generation) resolveNext() else resolving = false }
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    scope.launch {
                        if (myGeneration == generation) {
                            // IPv4 only: a bracketed IPv6 literal (and its
                            // zone id) is a second address format every
                            // caller of a plain "host:port" string — the
                            // manual override field, and pickLanServer's own
                            // hostOf() — would also have to learn, for a
                            // server that is, in practice, always reached
                            // over v4 on a home LAN.
                            (serviceInfo.host as? Inet4Address)?.hostAddress?.let { host ->
                                found.add(LanServer("http://$host:${serviceInfo.port}", "$host:${serviceInfo.port}"))
                                pick(myGeneration)
                            }
                            resolveNext()
                        } else {
                            resolving = false
                        }
                    }
                }
            },
        )
    }

    private fun pick(generationAtCall: Int) {
        scope.launch {
            val picked =
                runCatching { pickLanServer(found.toList(), manualAddress(), LanServerProbe { client.verify(it) }) }
                    .getOrNull()
            if (generationAtCall != generation) return@launch
            if (picked != null) {
                _server.value = picked
                endPass(generationAtCall)
            }
        }
    }

    private companion object {
        const val PASS_WINDOW_MS = 10_000L
    }
}

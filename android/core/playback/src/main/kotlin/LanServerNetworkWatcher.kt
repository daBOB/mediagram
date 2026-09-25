package playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

/**
 * Watches the device's default network and reacts through two plain
 * callbacks: [onLost] when it goes away — a server discovered on it is no
 * longer reachable, and holding onto its address would mean silently
 * failing every read against it for up to [LanFirstChunkSource]'s own down
 * window — and [onUnmeteredAvailable] once per network that becomes
 * unmetered, worth a fresh discovery pass (the third of the trigger,
 * alongside Settings opening and app start).
 *
 * `registerDefaultNetworkCallback` is registered once and never
 * unregistered: this watcher is owned by [LanServerLocator], a process-
 * lifetime singleton, so there is no shorter-lived owner to unregister it
 * from — the callback simply stops mattering when the process does.
 */
class LanServerNetworkWatcher(
    private val context: Context,
    private val onLost: () -> Unit,
    private val onUnmeteredAvailable: () -> Unit,
) {
    private val connectivityManager: ConnectivityManager? by lazy {
        context.getSystemService(ConnectivityManager::class.java)
    }

    @Volatile
    private var lastWasUnmetered = false

    fun start() {
        val manager = connectivityManager ?: return
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onLost(network: Network) {
                    lastWasUnmetered = false
                    onLost()
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    val unmetered = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                    if (unmetered && !lastWasUnmetered) {
                        lastWasUnmetered = true
                        onUnmeteredAvailable()
                    } else if (!unmetered) {
                        lastWasUnmetered = false
                    }
                }
            }
        runCatching { manager.registerDefaultNetworkCallback(callback) }
    }
}

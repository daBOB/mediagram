package com.mediagram.controller

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Join URLs of game nights the TV announces on the Wi-Fi (`_mediagram-game._tcp`, TXT `url`).
 * Runs until the collector stops; a box that comes back, or comes back elsewhere, is emitted again.
 */
class BoxFinder(
    context: Context,
) {
    private val app = context.applicationContext
    private val nsd = app.getSystemService(NsdManager::class.java)

    fun found(): Flow<String> =
        callbackFlow {
            val lock =
                app.getSystemService(WifiManager::class.java)?.createMulticastLock("spieltisch")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            val watching = mutableListOf<NsdManager.ServiceInfoCallback>()
            val emit = { info: NsdServiceInfo ->
                info.attributes["url"]
                    ?.let { String(it, Charsets.UTF_8) }
                    ?.let(::lanGameUrl)
                    ?.let { trySend(it) }
                Unit
            }
            val discovery =
                object : NsdManager.DiscoveryListener {
                    override fun onServiceFound(info: NsdServiceInfo) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            // stays registered, so a changed address arrives as an update
                            val callback = infoCallback(emit)
                            synchronized(watching) { watching += callback }
                            nsd.registerServiceInfoCallback(info, app.mainExecutor, callback)
                        } else {
                            @Suppress("DEPRECATION")
                            nsd.resolveService(info, resolveListener(emit))
                        }
                    }

                    override fun onServiceLost(info: NsdServiceInfo) = Unit

                    override fun onDiscoveryStarted(serviceType: String) = Unit

                    override fun onDiscoveryStopped(serviceType: String) = Unit

                    override fun onStartDiscoveryFailed(
                        serviceType: String,
                        errorCode: Int,
                    ) {
                        // the waiting screen's QR button still works
                        Log.w(TAG, "discovery failed to start: $errorCode")
                    }

                    override fun onStopDiscoveryFailed(
                        serviceType: String,
                        errorCode: Int,
                    ) = Unit
                }
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery)
            awaitClose {
                runCatching { nsd.stopServiceDiscovery(discovery) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    synchronized(watching) { watching.forEach { runCatching { nsd.unregisterServiceInfoCallback(it) } } }
                }
                lock?.release()
            }
        }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun infoCallback(emit: (NsdServiceInfo) -> Unit) =
        object : NsdManager.ServiceInfoCallback {
            override fun onServiceUpdated(info: NsdServiceInfo) = emit(info)

            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) = Unit

            override fun onServiceLost() = Unit

            override fun onServiceInfoCallbackUnregistered() = Unit
        }

    @Suppress("DEPRECATION")
    private fun resolveListener(emit: (NsdServiceInfo) -> Unit) =
        object : NsdManager.ResolveListener {
            override fun onServiceResolved(info: NsdServiceInfo) = emit(info)

            override fun onResolveFailed(
                info: NsdServiceInfo,
                errorCode: Int,
            ) = Unit
        }

    private companion object {
        const val SERVICE_TYPE = "_mediagram-game._tcp"
        const val TAG = "BoxFinder"
    }
}

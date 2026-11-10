package com.mediagram.spieltisch

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import java.net.URI

/**
 * Tells phones on the Wi-Fi that game night is open, so the Spieltisch controller apps can join
 * without a QR code. The join URL travels in the announcement itself: it is the address the QR
 * code shows, so the apps never have to work one out.
 */
interface GameNightAnnouncer {
    fun announce(joinUrl: String)

    /** Safe to call when nothing is announced. */
    fun withdraw()
}

/** DNS-SD (Bonjour) `_mediagram-game._tcp`, instance "Spieltisch", TXT `url=<joinUrl>`. */
class NsdGameNightAnnouncer(
    context: Context,
) : GameNightAnnouncer {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private var registered: NsdManager.RegistrationListener? = null

    @Synchronized
    override fun announce(joinUrl: String) {
        withdraw()
        val info =
            NsdServiceInfo().apply {
                serviceName = "Spieltisch"
                serviceType = SERVICE_TYPE
                port = URI(joinUrl).port
                setAttribute("url", joinUrl)
            }
        val listener =
            object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) = Unit

                override fun onRegistrationFailed(
                    info: NsdServiceInfo,
                    errorCode: Int,
                ) {
                    // Phones can still join by QR code; nothing else depends on this.
                    Log.w(TAG, "announcing game night failed: $errorCode")
                }

                override fun onServiceUnregistered(info: NsdServiceInfo) = Unit

                override fun onUnregistrationFailed(
                    info: NsdServiceInfo,
                    errorCode: Int,
                ) = Unit
            }
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        registered = listener
    }

    @Synchronized
    override fun withdraw() {
        val listener = registered ?: return
        registered = null
        try {
            nsd.unregisterService(listener)
        } catch (e: IllegalArgumentException) {
            // the registration had already failed: nothing left to withdraw
        }
    }

    private companion object {
        const val SERVICE_TYPE = "_mediagram-game._tcp"
        const val TAG = "GameNightAnnouncer"
    }
}

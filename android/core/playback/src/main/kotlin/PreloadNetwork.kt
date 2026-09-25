package playback

import android.content.Context
import android.net.ConnectivityManager

/**
 * Whether preloading may spend network right now — the "Wi-Fi only" rule a
 * viewer never has to find a setting for. An interface so a test can hold a
 * fixed answer without an Android `ConnectivityManager` in hand.
 */
fun interface UnmeteredNetworkCheck {
    fun isUnmetered(): Boolean
}

/**
 * Reads the system's own idea of "metered" — a mobile data connection, or
 * Wi-Fi a viewer has marked metered themselves, either of which this asks
 * nothing further about. `false` (never preload) when there is no active
 * network to ask, rather than guessing.
 */
class SystemUnmeteredNetworkCheck(private val context: Context) : UnmeteredNetworkCheck {
    override fun isUnmetered(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return !manager.isActiveNetworkMetered
    }
}

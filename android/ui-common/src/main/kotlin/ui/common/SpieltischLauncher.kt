package ui.common

import android.content.Context
import android.content.Intent

private const val SPIELTISCH_PACKAGE = "com.mediagram.spieltisch"

/** What the Games row says when Spieltisch is not on the device. */
const val SPIELTISCH_MISSING = "Spieltisch ist nicht installiert"

/**
 * Opens Spieltisch, the games' own app. `false` when it is not installed, has nothing to launch or
 * refuses the launch, in which case nothing was started. Spieltisch is a television app: its only launcher entry is the
 * leanback one, which the plain launch intent also finds on newer Android but not on every box.
 */
fun Context.openSpieltisch(): Boolean {
    val intent =
        packageManager.getLeanbackLaunchIntentForPackage(SPIELTISCH_PACKAGE)
            ?: packageManager.getLaunchIntentForPackage(SPIELTISCH_PACKAGE)
            ?: return false
    return try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: RuntimeException) {
        // Not found, refused (a launcher that isn't exported, an OEM restriction): all mean "can't open it".
        false
    }
}

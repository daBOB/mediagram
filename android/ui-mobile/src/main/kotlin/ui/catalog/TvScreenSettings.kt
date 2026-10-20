package ui.catalog

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The system's Cast screen, where a TV screen is connected and let go. An
 * ordinary app can't list Miracast TVs or connect to one, which takes a
 * system permission, but it can open this; on the tablet it is Xiaomi's Cast
 * page, with the living room TV in it.
 */
internal fun castSettingsIntent(): Intent = Intent(Settings.ACTION_CAST_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** Whether this device has the Cast screen to open: without it, Play on TV only offers to the TV box. */
internal fun canOpenCastSettings(context: Context): Boolean = castSettingsIntent().resolveActivity(context.packageManager) != null

/** Opens the Cast screen, or null where the device has none. */
@Composable
internal fun rememberOpenCastSettings(): (() -> Unit)? {
    val context = LocalContext.current
    return remember(context) {
        // A screen that resolved but refuses to open leaves the player as it was, rather than closing it.
        if (canOpenCastSettings(context)) ({ runCatching { context.startActivity(castSettingsIntent()) } }) else null
    }
}

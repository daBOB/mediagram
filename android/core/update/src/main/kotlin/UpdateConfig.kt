package update

import android.content.Context
import android.os.Build
import java.io.File

/**
 * What the app module knows about this build: whether it updates itself
 * (only the `release` build type does), which versionCode it is, and where
 * downloads go.
 */
data class UpdateConfig(
    val enabled: Boolean,
    val installedVersionCode: Long,
    val updatesDir: File,
)

/** Whether the player is playing right now. Reads the player on its own thread. */
fun interface PlaybackActivity {
    suspend fun isPlaying(): Boolean
}

/** The config of this build of the app owning [context]: [enabled], the installed versionCode, and downloads under the cache. */
fun updateConfig(
    context: Context,
    enabled: Boolean,
): UpdateConfig {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)

    @Suppress("DEPRECATION")
    val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    return UpdateConfig(enabled, code, File(context.cacheDir, "updates"))
}

package update

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

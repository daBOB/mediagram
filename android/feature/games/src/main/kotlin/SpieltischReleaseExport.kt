package games

import android.content.Context
import data.CoreProvider
import data.settings.LibrarySettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uniffi.mediagram_core.AppRelease
import uniffi.mediagram_core.CoreInterface
import uniffi.mediagram_core.ReleaseApp
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spieltisch's newest release, which only this app can read: it holds the Telegram session. The
 * channel's `#spieltisch-app` posts are looked up and downloaded by the core, which checks each
 * download against its caption before it counts; Spieltisch checks it again before installing.
 * Everything answers `null` when there is nobody to ask or nothing pinned.
 */
@Singleton
class SpieltischReleaseExport
    @Inject
    constructor(
        private val coreProvider: CoreProvider,
        private val settings: LibrarySettings,
        @ApplicationContext context: Context,
    ) {
        private val dir = File(context.cacheDir, "spieltisch-release")

        /** One download at a time, so two asks that come together fetch the APK once. */
        private val lock = Mutex()

        /** The newest release pinned under the library the person chose. */
        suspend fun latest(): AppRelease? = lookup()?.second

        /** The release's APK, in this app's cache; only the newest is kept. Waits for the download when there is none yet. */
        suspend fun apk(): File? =
            lock.withLock {
                val (core, release) = lookup() ?: return null
                val apk = File(dir, "${release.versionCode}.apk")
                dir.mkdirs()
                dir.listFiles().orEmpty().filter { it.name != apk.name }.forEach { it.delete() }
                // The core renames a download into place only once it matched its caption, so a file here is whole.
                if (apk.length() != release.bytes.toLong()) core.downloadAppRelease(release, apk.path)
                apk.takeIf { it.isFile }
            }

        private suspend fun lookup(): Pair<CoreInterface, AppRelease>? {
            val handle = settings.read() ?: return null
            val core = coreProvider.coreOrNull() ?: return null
            return core.latestAppRelease(handle, ReleaseApp.SPIELTISCH)?.let { core to it }
        }
    }

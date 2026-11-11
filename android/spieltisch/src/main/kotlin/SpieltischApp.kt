package com.mediagram.spieltisch

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import update.AppUpdater
import update.PackageApkInstaller
import update.UpdaterHost
import update.updateConfig
import java.io.File

/** Holds the one [NightSession] and the one [AppUpdater] every screen of this process shares. */
class SpieltischApp : Application(), UpdaterHost {
    /** Made with the process rather than the first screen, so its warm-up wakes the media app as early as it can. */
    lateinit var night: NightSession
        private set

    override lateinit var updater: AppUpdater
        private set

    override fun onCreate() {
        super.onCreate()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        night =
            NightSession(
                NativeSpieltischCore,
                NsdGameNightAnnouncer(this),
                scope,
                filesDir.path,
                MediaLibraryBridge(contentResolver),
                MediaImageCache(contentResolver, File(cacheDir, "media-images")),
            )
        updater =
            AppUpdater(
                updateConfig(this, enabled = resources.getBoolean(R.bool.self_update)),
                MediaReleaseSource(contentResolver),
                night.asPlayback(),
                PackageApkInstaller(this),
                scope,
            ).also { it.logStatus(scope) }
    }
}

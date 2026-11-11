package com.mediagram.spieltisch

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Holds the one [NightSession] every screen of this process shares. */
class SpieltischApp : Application() {
    /** Made with the process rather than the first screen, so its warm-up wakes the media app as early as it can. */
    lateinit var night: NightSession
        private set

    override fun onCreate() {
        super.onCreate()
        night =
            NightSession(
                NativeSpieltischCore,
                NsdGameNightAnnouncer(this),
                CoroutineScope(SupervisorJob() + Dispatchers.Default),
                filesDir.path,
                MediaLibraryBridge(contentResolver),
                MediaImageCache(contentResolver, File(cacheDir, "media-images")),
            )
    }
}

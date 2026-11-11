package com.mediagram.android

import android.app.Application
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import update.AppUpdater
import update.UpdaterHost
import javax.inject.Inject

@HiltAndroidApp
class MediagramApp : Application(), UpdaterHost {
    @Inject
    lateinit var appUpdater: Lazy<AppUpdater>

    /** Where `update.InstallResultReceiver` finds the updater to report an install's outcome to. */
    override val updater: AppUpdater get() = appUpdater.get()
}

package com.mediagram.android.di

import android.util.Log
import data.CoreProvider
import data.coreSentence
import data.settings.LibrarySettings
import kotlinx.coroutines.CancellationException
import uniffi.mediagram_core.CoreInterface
import update.Lookup
import update.Release
import update.ReleaseSource
import uniffi.mediagram_core.ReleaseApp
import javax.inject.Inject
import javax.inject.Singleton

/** The media app's own releases: the newest `#mlib-app` post pinned in the chosen library's channel, fetched by the core and verified there. */
@Singleton
class CoreReleaseSource
    @Inject
    constructor(
        private val coreProvider: CoreProvider,
        private val settings: LibrarySettings,
    ) : ReleaseSource {
        override suspend fun latest(): Lookup {
            val handle: String
            val core: CoreInterface
            try {
                handle = settings.read() ?: return Lookup.Unavailable()
                core = coreProvider.coreOrNull() ?: return Lookup.Unavailable()
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                // Never got as far as asking the channel, so the next foreground asks again instead of waiting out the hour.
                Log.w("update", "update check failed", e)
                return Lookup.Unavailable(e.coreSentence() ?: "the update check did not finish")
            }
            val release = core.latestAppRelease(handle, ReleaseApp.MEDIA_APP)
            // The core renames a download into place only once it matched its caption.
            return Lookup.Found(release?.let { Release(it.versionName, it.versionCode) { apk -> core.downloadAppRelease(it, apk.path) } })
        }

        override fun sentence(failure: Throwable): String? = failure.coreSentence()
    }

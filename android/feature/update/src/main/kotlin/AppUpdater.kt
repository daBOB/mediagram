package update

import android.content.Intent
import android.content.pm.PackageInstaller
import data.CoreProvider
import data.di.AppScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import settings.LibrarySettings
import uniffi.mediagram_core.AppRelease
import uniffi.mediagram_core.CoreInterface
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** How often a running download looks at the player. */
private const val PLAYBACK_POLL_MS = 2_000L

/**
 * Keeps a release build current with the newest release pinned in the
 * library channel: looks when the app comes to the foreground (at most
 * hourly, never while something plays), downloads it verified, and installs
 * it when the app goes to the background.
 */
@Singleton
class AppUpdater
    @Inject
    constructor(
        private val config: UpdateConfig,
        private val coreProvider: CoreProvider,
        private val settings: LibrarySettings,
        private val playback: PlaybackActivity,
        private val installer: ApkInstaller,
        @AppScope private val scope: CoroutineScope,
    ) {
        internal var now: () -> Long = System::currentTimeMillis

        private val _status = MutableStateFlow(if (config.enabled) UpdateStatus.NotChecked else UpdateStatus.Off)
        val status: StateFlow<UpdateStatus> = _status.asStateFlow()

        private var lastCheckAtMs: Long? = null
        private var ready: Ready? = null
        private var checking: Job? = null
        private var confirm: Intent? = null

        /** From `MainActivity.onStart`. */
        fun onForeground() {
            if (!config.enabled || checking?.isActive == true) return
            checking = scope.launch { checkAndDownload() }
        }

        /** From `MainActivity.onStop`. */
        fun onBackground() {
            if (config.enabled) scope.launch { installIfReady() }
        }

        /** A confirm screen Android asked for while the app was in the background, once, for the activity to show now that it is in front. */
        fun takeConfirmIntent(): Intent? = confirm.also { confirm = null }

        fun onInstallResult(
            status: Int,
            message: String?,
            confirm: Intent?,
        ) {
            when (status) {
                // The process is being replaced; the next launch is the new version.
                PackageInstaller.STATUS_SUCCESS -> Unit
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    this.confirm = confirm
                    ready?.let { _status.value = UpdateStatus.ConfirmWaiting(it.versionName) }
                }
                else -> _status.value = UpdateStatus.Failed(message ?: "Android refused the update ($status)")
            }
        }

        internal suspend fun checkAndDownload() {
            if (!shouldCheck(config.enabled, playback.isPlaying(), lastCheckAtMs, now())) return
            val handle = settings.read() ?: return
            val core = coreProvider.coreOrNull() ?: return
            lastCheckAtMs = now()
            try {
                val release = core.latestAppRelease(handle)
                if (release == null || !isNewer(release.versionCode, config.installedVersionCode)) {
                    ready = null
                    removeStale(wantedCode = null)
                    _status.value = UpdateStatus.UpToDate(now())
                    return
                }
                removeStale(wantedCode = release.versionCode)
                val apk = File(config.updatesDir, "${release.versionCode}.apk")
                // The core renames a download into place only once it matched its caption.
                if (!apk.exists()) {
                    _status.value = UpdateStatus.Downloading(release.versionName)
                    config.updatesDir.mkdirs()
                    if (!downloadWhileIdle(core, release, apk)) {
                        lastCheckAtMs = null
                        _status.value = UpdateStatus.NotChecked
                        return
                    }
                }
                ready = Ready(apk, release.versionName)
                _status.value = UpdateStatus.Ready(release.versionName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _status.value = UpdateStatus.Failed(e.message ?: e.javaClass.simpleName)
            }
        }

        internal suspend fun installIfReady() {
            val apk = ready ?: return
            if (playback.isPlaying()) return
            val refusal = installer.refusal(apk.file)
            if (refusal != null) {
                apk.file.delete()
                ready = null
                _status.value = UpdateStatus.Failed(refusal)
                return
            }
            try {
                installer.install(apk.file)
            } catch (e: Exception) {
                _status.value = UpdateStatus.Failed(e.message ?: e.javaClass.simpleName)
            }
        }

        /** `false` when playback started first: the download is dropped and the next check starts it again. */
        private suspend fun downloadWhileIdle(
            core: CoreInterface,
            release: AppRelease,
            apk: File,
        ): Boolean =
            coroutineScope {
                val download = async { core.downloadAppRelease(release, apk.path) }
                while (download.isActive) {
                    delay(PLAYBACK_POLL_MS)
                    if (download.isActive && playback.isPlaying()) {
                        download.cancel()
                        return@coroutineScope false
                    }
                }
                download.await()
                true
            }

        private fun removeStale(wantedCode: Long?) {
            val names = config.updatesDir.list()?.toList() ?: return
            staleFiles(names, wantedCode).forEach { File(config.updatesDir, it).delete() }
        }

        private data class Ready(
            val file: File,
            val versionName: String,
        )
    }

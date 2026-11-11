package update

import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
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
import java.io.File

/** How often a running download looks at the player. */
private const val PLAYBACK_POLL_MS = 2_000L
private const val TAG = "update"

/**
 * Keeps a release build current with the newest release [source] knows:
 * looks when the app comes to the foreground (at most hourly, never while
 * something plays), downloads it verified, and installs it when the app
 * goes to the background. One instance per process, made by the app.
 */
class AppUpdater(
    private val config: UpdateConfig,
    private val source: ReleaseSource,
    private val playback: PlaybackActivity,
    private val installer: ApkInstaller,
    private val scope: CoroutineScope,
) {
    internal var now: () -> Long = System::currentTimeMillis

    private val _status = MutableStateFlow(if (config.enabled) UpdateStatus.NotChecked else UpdateStatus.Off)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    private var lastCheckAtMs: Long? = null
    private var ready: Ready? = null
    private var checking: Job? = null
    private var confirm: Intent? = null

    /** Between `onForeground` and `onBackground`: the app is in front of the person. */
    @Volatile
    private var inFront = false

    /** From `MainActivity.onStart`. */
    fun onForeground() {
        inFront = true
        if (!config.enabled || checking?.isActive == true) return
        checking = scope.launch { checkAndDownload() }
    }

    /** From `MainActivity.onStop`. */
    fun onBackground() {
        inFront = false
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
            // A profile Android will not take must not hold the update back: the next try goes in without
            // it. Every profile goes, not only the one `ready` names: a result can reach a restarted process.
            else ->
                if (config.updatesDir.listFiles { file -> file.extension == "dm" }.orEmpty().count { it.delete() } > 0) {
                    Log.w(TAG, "update refused with its profile ($status, $message); retrying without it")
                } else {
                    dropReady(message ?: "Android refused the update ($status)")
                }
        }
    }

    internal suspend fun checkAndDownload() {
        if (!shouldCheck(config.enabled, playback.isPlaying(), lastCheckAtMs, now())) return
        try {
            val previousCheckAtMs = lastCheckAtMs
            lastCheckAtMs = now()
            val release =
                when (val lookup = source.latest()) {
                    // Nothing to ask yet: not a look at the channel, so the next foreground asks again.
                    is Lookup.Unavailable -> {
                        lastCheckAtMs = previousCheckAtMs
                        lookup.reason?.let { _status.value = UpdateStatus.Failed(it) }
                        return
                    }
                    is Lookup.Found -> lookup.release
                }
            if (release == null || !isNewer(release.versionCode, config.installedVersionCode)) {
                ready = null
                removeStale(wantedCode = null)
                _status.value = UpdateStatus.UpToDate(now())
                return
            }
            removeStale(wantedCode = release.versionCode)
            val apk = File(config.updatesDir, "${release.versionCode}.apk")
            // A newer release deleted the file the pointer named.
            if (ready?.file != apk) ready = null
            // The core renames a download into place only once it matched its caption.
            if (!apk.exists()) {
                _status.value = UpdateStatus.Downloading(release.versionName)
                config.updatesDir.mkdirs()
                if (!downloadWhileIdle(release, apk)) {
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
            Log.w(TAG, "update check failed", e)
            _status.value = UpdateStatus.Failed(source.sentence(e) ?: "the update check did not finish")
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
        // Back in front since this install was asked for: it would end what the person came back to. It stays ready for the next background.
        if (inFront) return
        try {
            installer.install(apk.file)
        } catch (e: Exception) {
            // An installer's own message names file paths, not anything a person can act on.
            Log.w(TAG, "update install failed", e)
            dropReady("the installer did not take the download")
        }
    }

    /** An APK that will not install is deleted, so the next check downloads afresh instead of retrying it. */
    private fun dropReady(reason: String) {
        ready?.file?.delete()
        ready = null
        _status.value = UpdateStatus.Failed(reason)
    }

    /** `false` when playback started first: the download is dropped and the next check starts it again. */
    private suspend fun downloadWhileIdle(
        release: Release,
        apk: File,
    ): Boolean =
        coroutineScope {
            val download = async { release.download(apk) }
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

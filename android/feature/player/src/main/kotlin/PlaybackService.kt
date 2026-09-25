package player

import android.app.PendingIntent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Lock-screen and notification controls for the app's single player — a
 * `MediaSession` wrapped around the same `ExoPlayer` [PlayerViewModel]
 * already drives, so play/pause/seek from a headset, the notification or
 * the lock screen land on it exactly the way the transport bar's own
 * buttons do. Media3 builds and manages the notification itself once this
 * service exists, holds a session and that session has been [addSession]ed
 * — building one alone is not enough, and nothing here posts a
 * notification by hand.
 *
 * [PlayerViewModel.open]/`stop` start and stop this service (through
 * [AndroidPlaybackServiceController]) — this class only ever waits for the
 * already-building player to finish and wraps it once, since the app's
 * singleton player is never rebuilt for a second session.
 *
 * Declared in this module's own `AndroidManifest.xml`, foreground service
 * type `mediaPlayback` — `:app` does not need to know this service exists.
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var handle: PlayerHandle

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            val player = handle.player.filterNotNull().first()
            val builder = MediaSession.Builder(this@PlaybackService, player)
            sessionActivityIntent()?.let(builder::setSessionActivity)
            val session = builder.build()
            mediaSession = session
            addSession(session)
        }
    }

    /**
     * What tapping the notification or the lock-screen entry reopens —
     * the app's own launcher activity, found by package rather than named
     * directly: this module sits below `:app` (`feature:player` never
     * depends on it), so `MainActivity` is not a class this file can
     * import. `null` only if the package manager cannot resolve a
     * launcher for this app's own package, which does not happen for an
     * installed, running instance of it.
     */
    private fun sessionActivityIntent(): PendingIntent? =
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

    /**
     * `null` until the deferred player above has finished building — a
     * controller that connects in that window (headset attached the
     * instant the service starts, before the cache has even opened) simply
     * finds nothing to control yet, rather than this blocking to wait for
     * one; nothing plays before then either.
     */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.let {
            removeSession(it)
            it.release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

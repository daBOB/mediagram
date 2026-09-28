package player

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import playback.ActivePreload
import playback.FilmPreloading
import javax.inject.Inject

/**
 * Keeps a film preload running while the app is backgrounded — a
 * `dataSync` foreground service, started by `di.PreloadModule` the moment
 * [FilmPreloading.hasWork] first turns `true`; this stops itself once it
 * turns `false`, or once [onTimeout] says Android's own ceiling for a
 * continuously running `dataSync` service (6h a run, 24h a day) has been
 * reached — [FilmPreloading.pauseForTimeLimit] surfaces that on the film
 * page, resumable from there. [onDestroy] mirrors that same pause for any
 * *other* reason this service stops while work remains: AOSP's own
 * `ActiveServices.maybeStopFgsTimeoutLocked` logs "Stop FGS timeout" on
 * every ordinary `dataSync` stop, including this class's own `hasWork`
 * self-stop below — it is bookkeeping cleanup, not the abuse-prevention
 * timer itself (that path is `onFgsTimeout`/[onTimeout]), so the log line
 * alone is not a sign anything is wrong. A real teardown from a cause this
 * class cannot name (a battery saver, a policy this device enforces,
 * anything else) is exactly what [onDestroy] now catches instead of
 * leaving a viewer with a preload that silently stopped moving.
 *
 * Stays foreground while a preload is merely *paused* (something opened
 * in the player, say) rather than stopping and letting [hasWork] restart
 * it later — a `dataSync` service cannot be started from the background
 * on API 31+, only from an already-foreground or user-visible context, so
 * giving it up here would strand a paused preload until the app itself is
 * foregrounded again. The cost is spending part of the 6h/24h budget
 * above during a long viewing session; accepted for now; worth revisiting
 * if that budget turns out to matter in practice for a real binge.
 *
 * No `POST_NOTIFICATIONS` (see the app manifest): [notification] is built
 * and kept current regardless, since [ServiceCompat.startForeground] needs
 * one to enter the foreground state at all, but without the permission the
 * system never actually shows it — the queue's own progress lives on the
 * film page, so nothing here is lost by that.
 *
 * The queue behind [preloader] is in memory only — a process death (not
 * just this service being asked to stop) loses it, the same as
 * `SeriesPreloader`. Worth persisting only if that turns out to matter in
 * practice; nothing here survives a swipe-kill today.
 */
@AndroidEntryPoint
class PreloadService : Service() {

    @Inject
    lateinit var preloader: FilmPreloading

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        NotificationManagerCompat.from(this).createNotificationChannel(channel())
        startForegroundDataSync()
        scope.launch { preloader.active.collect { active -> updateNotification(active) } }
        scope.launch { preloader.hasWork.filter { !it }.collect { stopSelf() } }
    }

    // FOREGROUND_SERVICE_TYPE_DATA_SYNC is a plain compile-time int
    // (API 29), inlined into the call site rather than resolved at
    // runtime — passing it below API 29 is exactly how ServiceCompat
    // itself expects to be called on every minSdk this app supports (24);
    // there is no runtime lookup that could fail on an older device.
    @Suppress("InlinedApi")
    private fun startForegroundDataSync() {
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(null), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    /** Android's own ceiling for this run, not a preload that has merely stalled — see the class doc. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        preloader.pauseForTimeLimit()
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Mirrors [onTimeout]'s own [FilmPreloading.pauseForTimeLimit] call —
     * see the class doc for why. Guarded on [FilmPreloading.hasWork]
     * itself, not called unconditionally: the *ordinary* stop this service
     * asks for once the queue empties (`hasWork.filter { !it }` above)
     * already reaches here with nothing left to pause — calling it
     * unconditionally would be a same-thread no-op in that case (an empty
     * queue), but would risk quietly pausing whatever a viewer enqueued in
     * the brief window between that empty read and this method actually
     * running. Reading [FilmPreloading.hasWork] synchronously right here
     * closes that window: `true` only when real work is still genuinely
     * outstanding at the moment of teardown.
     */
    override fun onDestroy() {
        if (preloader.hasWork.value) {
            preloader.pauseForTimeLimit()
        }
        scope.cancel()
        super.onDestroy()
    }

    // No POST_NOTIFICATIONS (see the app manifest): on API 33+ without it,
    // the platform documents this as a silent no-op, not a thrown
    // SecurityException — the same "runs, just never shown" the service's
    // own class doc describes.
    @SuppressLint("MissingPermission", "NotificationPermission")
    private fun updateNotification(active: ActivePreload?) {
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification(active))
    }

    private fun notification(active: ActivePreload?) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(active?.let { "Preloading ${it.title} · ${percent(it)}%" } ?: "Preloading")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()

    private fun percent(active: ActivePreload): Int =
        if (active.totalBytes <= 0) 0 else ((active.heldBytes * 100) / active.totalBytes).toInt().coerceIn(0, 100)

    private fun channel() =
        NotificationChannelCompat
            .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName("Preloading")
            .build()

    private companion object {
        const val CHANNEL_ID = "film_preload"
        const val NOTIFICATION_ID = 4_201
    }
}

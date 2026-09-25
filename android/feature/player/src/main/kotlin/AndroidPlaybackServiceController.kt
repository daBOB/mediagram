package player

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Real implementation of [PlaybackServiceController], started with a plain
 * `startService` rather than `startForegroundService`: every call site is a
 * viewer actively opening a title from the foreground UI, never a
 * background wake-up, so the five-second window Android gives a
 * foreground-started service to call `startForeground()` itself is not a
 * risk here — [PlaybackService] promotes itself once media3's own
 * notification manager decides there is something to show, which a title
 * requested with the autoplay gate held (`playWhenReady = false`) may not
 * do right away.
 */
class AndroidPlaybackServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlaybackServiceController {

    override fun start() {
        context.startService(Intent(context, PlaybackService::class.java))
    }

    override fun stop() {
        context.stopService(Intent(context, PlaybackService::class.java))
    }
}

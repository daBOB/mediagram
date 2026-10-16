// media3's effect and analytics surfaces are @UnstableApi; see CacheProvider
// for why the version is pinned.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package playback

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener

/**
 * Spike: an optional sharpening filter, toggled per title by a system
 * property so one build serves both arms of the measurement. Logged with
 * `Log.e` because the release R8 rules strip `Log.v/d/i/w`.
 */
const val VIDEO_FILTER_TAG = "MgFilter"

private const val PROPERTY = "debug.mediagram.videofilter"
private const val SHARPNESS = 0.5f
private const val TICK_MS = 10_000L

/** `1` means on; anything else, or unset, means off. Read through reflection: `SystemProperties` is hidden API. */
private fun filterWanted(): Boolean = try {
    Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, PROPERTY) == "1"
} catch (e: ReflectiveOperationException) {
    Log.e(VIDEO_FILTER_TAG, "could not read $PROPERTY", e)
    false
}

/**
 * Called before every `prepare`. Effects only take hold when the video
 * renderer is enabled, and once media3 has built its video graph for a
 * renderer it keeps it for the player's life — so "off" never calls
 * `setVideoEffects` on a player that has not had the filter, or the
 * graph would be built (and paid for) with nothing in it.
 */
fun applyVideoFilter(player: ExoPlayer) {
    val wanted = filterWanted()
    VideoFilterLog.requested = wanted
    when {
        wanted -> player.setVideoEffects(listOf(CasSharpenEffect(SHARPNESS)))
        VideoFilterLog.everApplied -> player.setVideoEffects(emptyList())
    }
    if (wanted) VideoFilterLog.everApplied = true
    Log.e(VIDEO_FILTER_TAG, "open: filter=${if (wanted) "on" else "off"} graphBuilt=${VideoFilterLog.everApplied}")
}

/**
 * Logs the video renderer's frame counters every [TICK_MS] while video is
 * enabled, and once more when it is disabled (a title closing or the next
 * one opening). Also takes the filter out again for HDR, which the video
 * graph would otherwise tone-map.
 */
class VideoFilterLog(private val player: ExoPlayer) : AnalyticsListener {
    private val main = Handler(Looper.getMainLooper())
    private var counters: DecoderCounters? = null
    private var format: Format? = null
    private var decoder: String? = null
    private val tick = object : Runnable {
        override fun run() {
            log("tick")
            main.postDelayed(this, TICK_MS)
        }
    }

    override fun onVideoEnabled(eventTime: AnalyticsListener.EventTime, decoderCounters: DecoderCounters) {
        counters = decoderCounters
        main.removeCallbacks(tick)
        main.postDelayed(tick, TICK_MS)
    }

    override fun onVideoDisabled(eventTime: AnalyticsListener.EventTime, decoderCounters: DecoderCounters) {
        main.removeCallbacks(tick)
        counters = decoderCounters
        log("release")
        counters = null
    }

    override fun onVideoDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long,
    ) {
        decoder = decoderName
    }

    override fun onVideoInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) {
        this.format = format
        val hdr = ColorInfo.isTransferHdr(format.colorInfo)
        Log.e(VIDEO_FILTER_TAG, "format ${format.width}x${format.height} ${format.frameRate}fps ${format.sampleMimeType} transfer=${format.colorInfo?.colorTransfer} hdr=$hdr")
        if (hdr && requested) {
            player.setVideoEffects(emptyList())
            requested = false
            Log.e(VIDEO_FILTER_TAG, "HDR: filter removed for this title")
        }
    }

    private fun log(what: String) {
        val c = counters ?: return
        c.ensureUpdated()
        val f = format
        Log.e(
            VIDEO_FILTER_TAG,
            "$what filter=${if (requested) "on" else "off"} res=${f?.width}x${f?.height} pos=${player.currentPosition} " +
                "rendered=${c.renderedOutputBufferCount} dropped=${c.droppedBufferCount} maxConsec=${c.maxConsecutiveDroppedBufferCount} " +
                "skipped=${c.skippedOutputBufferCount} droppedToKey=${c.droppedToKeyframeCount} casFrames=${CasSharpenEffect.framesDrawn.get()} decoder=$decoder",
        )
    }

    companion object {
        /** Whether the title now open asked for the filter (and is not HDR). */
        @Volatile var requested = false

        /** Whether this player has ever had effects set — after which its video graph stays. */
        @Volatile var everApplied = false
    }
}

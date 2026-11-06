// media3's renderer surface is @UnstableApi; see CacheProvider for why the
// version is pinned.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package playback

import android.util.Log
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

private const val PROPERTY = "debug.mediagram.videofilter"
private const val SHARPNESS = 0.5f

/** `1` means on; anything else, or unset, means off. Read through reflection: `SystemProperties` is hidden API. */
private fun sharpenWanted(): Boolean = try {
    Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, PROPERTY) == "1"
} catch (e: ReflectiveOperationException) {
    Log.e(VIDEO_FILTER_TAG, "could not read $PROPERTY", e)
    false
}

/**
 * Picks which of the two video renderers (see [PlaybackRenderersFactory])
 * plays the open title. The effects renderer takes it, with the
 * deinterlacing effect when [deinterlace] and the debug sharpening filter
 * when its system property is set; otherwise the plain one does, and the GL
 * path stays out of it. [hdr] forces the plain one: the video graph would
 * tone-map HDR.
 *
 * Called before every `prepare` with [deinterlace] off, and again whenever
 * the viewer flips the switch; the second call makes the player pick its
 * tracks again, which re-buffers at the current position.
 */
fun ExoPlayer.routeVideo(deinterlace: Boolean, hdr: Boolean = false) {
    val index = (0 until rendererCount).firstOrNull { getRenderer(it) is EffectsVideoRenderer } ?: return
    val renderer = getRenderer(index) as EffectsVideoRenderer
    val sharpen = sharpenWanted()
    val effects = listOfNotNull(
        DeinterlaceEffect { renderer.sourceLines }.takeIf { deinterlace },
        CasSharpenEffect(SHARPNESS).takeIf { sharpen },
    )
    val on = effects.isNotEmpty() && !hdr
    // Effects first, so the renderer has its list by the time the selection below enables it.
    if (on) setVideoEffects(effects)
    renderer.active = on
    VideoFilterLog.requested = on
    // The renderer claims nothing while inactive; changing a parameter is what makes the selector choose again.
    (trackSelector as? DefaultTrackSelector)?.let { selector ->
        selector.parameters = selector.buildUponParameters().setRendererDisabled(index, !on).build()
    }
    Log.e(VIDEO_FILTER_TAG, "route: effects=${if (on) "on" else "off"} deinterlace=$deinterlace sharpen=$sharpen hdr=$hdr")
}

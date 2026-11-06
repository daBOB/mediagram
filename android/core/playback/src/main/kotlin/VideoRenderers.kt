// media3's renderer surface is @UnstableApi; see CacheProvider for why the
// version is pinned.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer

/**
 * The video renderer every title uses unless it wants a GL effect. It drops
 * `MSG_SET_VIDEO_EFFECTS`: media3 sends that to every video renderer, and a
 * renderer given a non-null list builds a GL video graph on its next enable
 * and never lets go of it — the graph costs 4K and HDR dropped frames, so
 * this renderer must never see one. See [EffectsVideoRenderer] for the other.
 */
internal class PlainVideoRenderer(builder: MediaCodecVideoRenderer.Builder) : MediaCodecVideoRenderer(builder) {
    override fun handleMessage(messageType: Int, message: Any?) {
        if (messageType == Renderer.MSG_SET_VIDEO_EFFECTS) return
        super.handleMessage(messageType, message)
    }
}

/**
 * The video renderer that accepts effects. It lists first, so while it
 * claims formats it wins track selection over [PlainVideoRenderer]; while
 * [active] is false it claims nothing, so it is never enabled and never
 * builds a graph. [routeVideo] flips it and nudges the track selector to
 * choose again.
 */
internal class EffectsVideoRenderer(builder: MediaCodecVideoRenderer.Builder) : MediaCodecVideoRenderer(builder) {
    @Volatile var active = false

    /**
     * The open title's decoded height in lines, set on the playback thread
     * before any of its frames reach an effect — see [DeinterlaceEffect].
     */
    @Volatile var sourceLines = 0
        private set

    override fun supportsFormat(mediaCodecSelector: MediaCodecSelector, format: Format): Int =
        if (active) super.supportsFormat(mediaCodecSelector, format) else RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)

    override fun onInputFormatChanged(formatHolder: FormatHolder): DecoderReuseEvaluation? {
        formatHolder.format?.let { sourceLines = it.height }
        return super.onInputFormatChanged(formatHolder)
    }
}

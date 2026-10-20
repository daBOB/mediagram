package playback

import android.content.Context
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import data.isTelevision

/**
 * The platform audio renderer, except that it asks the decoder for a stereo
 * downmix whenever the output cannot play surround itself.
 *
 * Left alone, a 5.1 AAC track decodes to six channels — media3 lifts the
 * decoder's channel cap so a spatializer has every channel to place, and the
 * platform AAC decoder outputs all six by default anyway — and the six go to
 * an AudioTrack, trusting the platform mixer to fold them down for two
 * speakers. MIUI 14 on a Xiaomi Pad 5 Pro does not: it forces a six-channel
 * track onto a direct output, and whatever folds it down there leaves the
 * centre channel, which is the dialogue, sounding under water while music and
 * effects in the other channels sound fine. Asked for two channels, the
 * decoder downmixes itself, by the stream's own downmix levels or the
 * standard ones, and only stereo ever reaches the output.
 *
 * Surround is kept where something can render it: a spatializer that is on,
 * an output media3 reads as more than two speakers (an HDMI receiver over
 * ARC or eARC, a USB interface), and a television. The box's plain HDMI port
 * is one media3 does not read and takes for stereo, while its mixer already
 * fits PCM to whatever is on the other end of the cable.
 *
 * The choice is made when the decoder is configured, so a route change in the
 * middle of a title takes effect with the next one.
 */
@UnstableApi
internal class StereoDownmixAudioRenderer(
    context: Context,
    codecAdapterFactory: MediaCodecAdapter.Factory,
    mediaCodecSelector: MediaCodecSelector,
    enableDecoderFallback: Boolean,
    eventHandler: Handler?,
    eventListener: AudioRendererEventListener?,
    private val sink: AudioSink,
) : MediaCodecAudioRenderer(context, codecAdapterFactory, mediaCodecSelector, enableDecoderFallback, eventHandler, eventListener, sink) {
    private val television = isTelevision(context)

    /** Public rather than protected so a test can read what the decoder is asked for. */
    public override fun getMediaFormat(
        format: Format,
        codecMimeType: String,
        codecMaxInputSize: Int,
        codecOperatingRate: Float,
    ): MediaFormat {
        val mediaFormat = super.getMediaFormat(format, codecMimeType, codecMaxInputSize, codecOperatingRate)
        // The key is the platform's from API 32; older decoders don't read it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2 && !television && !playsSurround(sink.audioCapabilities)) {
            mediaFormat.setInteger(MediaFormat.KEY_MAX_OUTPUT_CHANNEL_COUNT, 2)
        }
        return mediaFormat
    }
}

/** Whether the current output renders more than two channels itself, as media3 reads it; unknown counts as stereo. */
@UnstableApi
private fun playsSurround(capabilities: AudioCapabilities?): Boolean =
    capabilities != null &&
        (capabilities.spatializerChannelMasks + capabilities.speakerLayoutChannelMasks).any { Integer.bitCount(it) > 2 }

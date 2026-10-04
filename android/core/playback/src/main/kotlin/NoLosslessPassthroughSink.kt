package playback

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink

/**
 * Refuses DTS and TrueHD as compressed passthrough, so the platform audio
 * renderer declines those tracks and FFmpeg (core:ffmpeg) decodes them to PCM.
 *
 * A Realtek Google TV box (RTD1325) advertises both as HDMI passthrough, then
 * stalls its compressed output on every buffer — AudioFlinger "pause because
 * of UNDERRUN" with hundreds of thousands of frames ready — so the film sits
 * at 0:00 with no sound and no message. It has no DTS or TrueHD decoder of its
 * own, so passthrough was the only way the platform renderer could take them.
 * AC-3 and E-AC-3 passthrough work there and keep going straight through.
 *
 * The cost: a receiver behind HDMI now gets DTS and TrueHD as decoded PCM
 * rather than the bitstream (DTS-HD MA as its core). Every sound system plays
 * PCM; a stalled bitstream plays nothing.
 */
@UnstableApi
internal class NoLosslessPassthroughSink(
    sink: AudioSink,
) : ForwardingAudioSink(sink) {
    override fun getFormatSupport(format: Format): Int =
        if (format.sampleMimeType in REFUSED) AudioSink.SINK_FORMAT_UNSUPPORTED else super.getFormatSupport(format)

    override fun supportsFormat(format: Format): Boolean = getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED

    private companion object {
        val REFUSED =
            setOf(
                MimeTypes.AUDIO_DTS,
                MimeTypes.AUDIO_DTS_HD,
                MimeTypes.AUDIO_DTS_EXPRESS,
                MimeTypes.AUDIO_DTS_X,
                MimeTypes.AUDIO_TRUEHD,
            )
    }
}

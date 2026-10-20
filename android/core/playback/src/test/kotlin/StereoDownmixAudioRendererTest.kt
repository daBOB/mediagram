package playback

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioProfile
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.AudioDeviceInfoBuilder
import org.robolectric.shadows.AudioProfileBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the platform decoder is asked for when a 5.1 AAC track meets each kind
 * of output. The capabilities are media3's own, read off a Robolectric device
 * of each type, so the test sees the same speaker layout the player would.
 *
 * API 35: Robolectric's AudioProfileBuilder can't build a profile on 36, and
 * 35 reads built-in speakers the way the reported Android 13 tablet does —
 * as stereo, with no speaker layout from the HAL.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StereoDownmixAudioRendererTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val surroundAac =
        Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setChannelCount(6).setSampleRate(48_000).build()

    private fun maxOutputChannels(capabilities: AudioCapabilities): Int {
        val sink =
            object : ForwardingAudioSink(DefaultAudioSink.Builder(context).build()) {
                override fun getAudioCapabilities() = capabilities
            }
        val renderer =
            StereoDownmixAudioRenderer(context, MediaCodecAdapter.Factory.getDefault(context), MediaCodecSelector.DEFAULT, false, null, null, sink)
        val mediaFormat = renderer.getMediaFormat(surroundAac, MimeTypes.AUDIO_AAC, 0, -1f)
        renderer.release()
        return mediaFormat.getInteger(MediaFormat.KEY_MAX_OUTPUT_CHANNEL_COUNT)
    }

    private fun routedTo(
        type: Int,
        vararg pcmChannelMasks: Int,
        spatializerChannelMasks: List<Int> = emptyList(),
    ): AudioCapabilities {
        val pcm =
            AudioProfileBuilder
                .newBuilder()
                .setFormat(AudioFormat.ENCODING_PCM_16BIT)
                .setSamplingRates(intArrayOf(48_000))
                .setChannelMasks(pcmChannelMasks)
                .setChannelIndexMasks(intArrayOf())
                .setEncapsulationType(AudioProfile.AUDIO_ENCAPSULATION_TYPE_NONE)
                .build()
        val device = AudioDeviceInfoBuilder.newBuilder().setType(type).setProfiles(listOf(pcm)).build()
        return AudioCapabilities.getCapabilities(context, AudioAttributes.DEFAULT, device, spatializerChannelMasks)
    }

    /**
     * The reported case is the first: a tablet's own speakers, where six
     * channels went to a direct output and the dialogue came out under water.
     * Headphones of every kind are stereo too.
     */
    @Test
    fun surroundIsDownmixedToStereoForOutputsThatPlayTwoChannels() {
        val stereo = AudioFormat.CHANNEL_OUT_STEREO
        for (type in listOf(
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_HEADSET,
        )) {
            assertEquals(2, maxOutputChannels(routedTo(type, stereo)), "output type $type must get a stereo downmix")
        }
    }

    @Test
    fun anHdmiReceiverThatTakesSurroundGetsEveryChannel() {
        val capabilities = routedTo(AudioDeviceInfo.TYPE_HDMI_ARC, AudioFormat.CHANNEL_OUT_5POINT1, AudioFormat.CHANNEL_OUT_STEREO)

        assertTrue(maxOutputChannels(capabilities) >= 6)
    }

    /** media3 takes a TV box's plain HDMI port for stereo; the box's own mixer fits PCM to the far end, so it keeps 5.1. */
    @Test
    fun aTelevisionOnPlainHdmiGetsEveryChannel() {
        shadowOf(context.getSystemService(UiModeManager::class.java)).setCurrentModeType(Configuration.UI_MODE_TYPE_TELEVISION)
        val capabilities = routedTo(AudioDeviceInfo.TYPE_HDMI, AudioFormat.CHANNEL_OUT_5POINT1, AudioFormat.CHANNEL_OUT_STEREO)

        assertTrue(maxOutputChannels(capabilities) >= 6)
    }

    @Test
    fun aSpatializerThatIsOnGetsEveryChannelToPlace() {
        val capabilities =
            routedTo(
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioFormat.CHANNEL_OUT_STEREO,
                spatializerChannelMasks = listOf(AudioFormat.CHANNEL_OUT_5POINT1),
            )

        assertTrue(maxOutputChannels(capabilities) >= 6)
    }

    @Test
    fun theFactoryBuildsThePlatformAudioRendererAsTheDownmixingOne() {
        val renderers =
            renderersFactory(context).createRenderers(
                Handler(Looper.getMainLooper()),
                object : VideoRendererEventListener {},
                object : AudioRendererEventListener {},
                { _: CueGroup -> },
                { _: Metadata -> },
            )

        assertTrue(renderers.filterIsInstance<MediaCodecAudioRenderer>().single() is StereoDownmixAudioRenderer)
        renderers.forEach { it.release() }
    }
}

package ffmpeg

import androidx.media3.common.MimeTypes
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On a real device or emulator, not Robolectric: the point is that the
 * library scripts/build-android-ffmpeg.sh produced actually loads on this
 * ABI and carries the decoders it was configured with — a Kotlin-only test
 * cannot see either.
 */
@RunWith(AndroidJUnit4::class)
class FfmpegLibraryTest {
    @Test
    fun theNativeLibraryLoads() {
        assertTrue("libffmpegJNI.so did not load", FfmpegLibrary.isAvailable())
    }

    @Test
    fun itDecodesDtsAndTrueHd() {
        assertTrue(FfmpegLibrary.supportsFormat(MimeTypes.AUDIO_DTS))
        assertTrue(FfmpegLibrary.supportsFormat(MimeTypes.AUDIO_DTS_HD))
        assertTrue(FfmpegLibrary.supportsFormat(MimeTypes.AUDIO_TRUEHD))
    }

    /** The build enables only what the device may lack; AAC stays with the platform. */
    @Test
    fun itCarriesNothingElse() {
        assertFalse(FfmpegLibrary.supportsFormat(MimeTypes.AUDIO_AAC))
    }
}

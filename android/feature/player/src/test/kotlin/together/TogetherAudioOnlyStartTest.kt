package player.together

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import io.mockk.every
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import player.heardTracks
import player.isAudioOnly
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A cast to the TV, or a join, that offers a title playing starts at its first frame; an MP3 renders none,
 * so the arrival of its audio-only tracks has to be the start. Robolectric: a real `TrackGroup` logs.
 */
@RunWith(RobolectricTestRunner::class)
class TogetherAudioOnlyStartTest {
    private fun group(
        type: Int,
        mime: String,
    ) = Tracks.Group(TrackGroup(Format.Builder().setSampleMimeType(mime).build()), false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true))
        .also { check(it.type == type) }

    private val audio = Tracks(listOf(group(C.TRACK_TYPE_AUDIO, "audio/mpeg")))
    private val film = Tracks(listOf(group(C.TRACK_TYPE_VIDEO, "video/avc"), group(C.TRACK_TYPE_AUDIO, "audio/mp4a-latm")))

    @Test
    fun aJoinPlaysAnAudioOnlyTitle() =
        runTest {
            val rig = casting()
            every { rig.player.playWhenReady } returns false

            rig.controller.heardTracks(Tracks.EMPTY)
            runCurrent()
            assertFalse(rig.handle.playCalled, "the empty list every title starts with is not an answer")

            rig.controller.heardTracks(audio)
            runCurrent()
            assertTrue(rig.handle.playCalled)
        }

    @Test
    fun aFilmWithoutItsFirstFrameDoesNotPlayFromItsTracks() =
        runTest {
            val rig = casting()
            every { rig.player.playWhenReady } returns false

            rig.controller.heardTracks(film)
            runCurrent()

            assertFalse(rig.handle.playCalled)
        }

    @Test
    fun onlyTracksWithNoVideoAreAudioOnly() {
        assertTrue(isAudioOnly(audio))
        assertFalse(isAudioOnly(film))
        assertFalse(isAudioOnly(Tracks.EMPTY))
    }
}

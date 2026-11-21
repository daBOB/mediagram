package player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import data.ArmedCast
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import model.Profile
import model.together.Member
import model.together.Playback
import model.together.offered
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import testing.WatchStateFixture
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A cast the TV was offered while playing starts when the player reports the tracks of an audio-only title,
 * through the ViewModel's own player listener: an MP3 never renders the first frame the start otherwise waits for.
 * Robolectric: a real `TrackGroup` logs.
 */
@RunWith(RobolectricTestRunner::class)
class PlayerAudioOnlyCastTest {
    @After
    fun tearDown() = Dispatchers.resetMain()

    private val audioOnly =
        Tracks(
            listOf(
                Tracks.Group(
                    TrackGroup(Format.Builder().setSampleMimeType("audio/mpeg").build()),
                    false,
                    intArrayOf(C.FORMAT_HANDLED),
                    booleanArrayOf(true),
                ),
            ),
        )

    @Test
    fun aPlayingCastOfAnAudioOnlyTitleStartsWhenItsTracksArrive() =
        runTest {
            installMainDispatcher()
            val rig = PlayerTogetherRig(this, WatchStateFixture(listOf(Profile("A", "Anna")), chosen = "A"))
            rig.put(960, offered("s-cast", Member("tab-1", "Tablet"), Playback("film-1", true, pos = 0.0, rate = 1.0), "A", "home", System.currentTimeMillis()))
            rig.live.refresh()
            rig.live.castOnOpen(ArmedCast(960, "film-1", "A", "Tablet", true))
            rig.vm.open("film-1")
            runCurrent()
            assertFalse(rig.message(960).playing)

            val listeners = mutableListOf<Player.Listener>()
            verify { rig.player.addListener(capture(listeners)) }
            listeners.forEach { it.onTracksChanged(audioOnly) }
            runCurrent()

            assertTrue(rig.message(960).playing)
            rig.clear()
        }
}

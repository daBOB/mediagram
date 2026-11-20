package player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The stage swaps the picture for the cover only once the player has said the title has none. */
@RunWith(RobolectricTestRunner::class)
class PlayerAudioOnlyTest {
    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun tracks(vararg mimes: String) =
        Tracks(mimes.map { Tracks.Group(TrackGroup(Format.Builder().setSampleMimeType(it).build()), false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true)) })

    @Test
    fun audioOnlyIsFalseUntilTracksArrive() =
        runTest {
            installMainDispatcher()
            val rig = PlayerTogetherRig(this)
            rig.vm.open("film-1")
            runCurrent()
            val listeners = mutableListOf<Player.Listener>()
            verify { rig.player.addListener(capture(listeners)) }
            fun report(t: Tracks) = listeners.forEach { it.onTracksChanged(t) }

            assertFalse(rig.vm.audioOnly.value)
            report(Tracks.EMPTY)
            assertFalse(rig.vm.audioOnly.value, "the empty first report is no answer")
            report(tracks("audio/mpeg"))
            assertTrue(rig.vm.audioOnly.value)

            rig.vm.open("e1")
            runCurrent()
            assertFalse(rig.vm.audioOnly.value)
            report(tracks("video/avc", "audio/mp4a-latm"))
            assertFalse(rig.vm.audioOnly.value)
            rig.clear()
        }
}

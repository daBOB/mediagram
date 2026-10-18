package player

import androidx.lifecycle.ViewModelStore
import androidx.media3.common.Player
import androidx.media3.common.util.Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import model.together.CastWords
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import playback.SKIP_MS
import player.together.EP1
import player.together.EP2
import player.together.TV
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The remote on a phone: it joins as a remote, and each press on the player card is a move of the TV's session. Following the TV is `CastRemoteFollowingTest`'s. */
@RunWith(RobolectricTestRunner::class)
class CastRemoteViewModelTest {
    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun openingJoinsAsARemote() =
        runTest {
            val rig = castRemote()

            val me = rig.sent.members.single { it.screen != TV.screen }
            assertEquals("Phone", me.label)
            assertTrue(me.remote)
            val ui = rig.vm.ui.value!!
            assertEquals(CAST_ID, ui.id)
            assertEquals(CastWords.PLAYING_ON_TV, ui.label)
            assertEquals("Pilot", ui.title)
            assertEquals("/b/ep1.jpg", ui.backdrop)
            assertEquals(Player.STATE_READY, rig.vm.player.playbackState)
            assertTrue(rig.vm.player.playWhenReady)
        }

    @Test
    fun pauseSendsPlayingFalse() =
        runTest {
            val rig = castRemote()
            advanceTimeBy(10_000)

            Util.handlePlayPauseButtonAction(rig.vm.player)
            runCurrent()

            assertFalse(rig.sent.playing)
            assertEquals(110.0, rig.sent.pos, "at the session's expected position")
            assertFalse(rig.vm.player.playWhenReady)
        }

    @Test
    fun seekSendsThePosition() =
        runTest {
            val rig = castRemote()

            rig.vm.player.seekTo(61_500L)
            runCurrent()

            assertEquals(61.5, rig.sent.pos)
            assertTrue(rig.sent.playing)
            assertEquals(61_500L, rig.vm.player.currentPosition)
        }

    @Test
    fun skipStepsByThePlayersOwnIncrement() =
        runTest {
            val rig = castRemote()

            assertEquals(SKIP_MS, rig.vm.player.seekBackIncrement)
            assertEquals(SKIP_MS, rig.vm.player.seekForwardIncrement)
            rig.vm.player.seekForward()
            runCurrent()

            assertEquals(115.0, rig.sent.pos)
        }

    @Test
    fun speedSendsTheRate() =
        runTest {
            val rig = castRemote()

            rig.vm.player.setPlaybackSpeed(1.5f)
            runCurrent()

            assertEquals(1.5, rig.sent.rate)
            assertEquals(1.5f, rig.vm.player.playbackParameters.speed)
        }

    @Test
    fun positionFollowsTheSessionClock() =
        runTest {
            val rig = castRemote()

            advanceTimeBy(5_000)

            assertEquals(105_000L, rig.vm.player.currentPosition)
            assertEquals(1_500_000L, rig.vm.player.duration)
        }

    @Test
    fun germanSubtitlesSendTextDe() =
        runTest {
            val rig = castRemote()
            assertEquals(CAST_ON_TV.cast!!.tracks, rig.vm.ui.value!!.tracks)

            rig.vm.chooseText("de")
            runCurrent()

            assertEquals("de", rig.sent.cast!!.text)
            assertEquals("a1", rig.sent.cast!!.audio)
            assertEquals("de", rig.vm.ui.value!!.text)
        }

    @Test
    fun stopEndsAndCloses() =
        runTest {
            val rig = castRemote()

            rig.vm.stop()
            runCurrent()

            assertTrue(rig.sent.ended)
            assertNull(rig.vm.ui.value)
            assertFalse(CAST_ID in rig.core.togetherMessages)
        }

    @Test
    fun nextEpisodeSendsTheNextSet() =
        runTest {
            val rig = castRemote()
            assertTrue(rig.vm.ui.value!!.hasNext)

            rig.vm.next()
            runCurrent()

            assertEquals(EP2, rig.sent.set)
            assertEquals(0.0, rig.sent.pos)
            assertTrue(rig.sent.playing)
        }

    @Test
    fun noNextHidesNextEpisode() =
        runTest {
            val rig = castRemote(run = listOf(EP1))
            val edits = rig.core.togetherEdits.size

            rig.vm.next()
            runCurrent()

            assertFalse(rig.vm.ui.value!!.hasNext)
            assertEquals(edits, rig.core.togetherEdits.size)
        }

    @Test
    fun closingLeaves() =
        runTest {
            val rig = castRemote()

            rig.vm.close()
            runCurrent()

            assertEquals(listOf(TV), rig.message().members)
            assertNull(rig.vm.ui.value)
        }

    @Test
    fun theModelGoingAwayLeaves() =
        runTest {
            val rig = castRemote()

            ViewModelStore().apply { put("remote", rig.vm) }.clear()
            runCurrent()

            assertEquals(listOf(TV), rig.message().members)
        }
}

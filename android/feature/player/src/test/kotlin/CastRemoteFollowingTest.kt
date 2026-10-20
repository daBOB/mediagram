package player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import data.TOGETHER_UNREACHABLE
import model.together.CastWords
import model.together.finished
import model.together.left
import model.together.writeSession
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import player.together.EP2
import player.together.HIDDEN
import player.together.NOW
import player.together.TV
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The remote following the TV's session: its edits and their notices, a read
 * that catches up, a title change, and the session going.
 */
@RunWith(RobolectricTestRunner::class)
class CastRemoteFollowingTest {
    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun aTvPauseSaysPausedOnTv() =
        runTest {
            val rig = castRemote()

            rig.fromTv(rig.message().copy(playing = false, pos = 105.0, at = NOW, by = TV.screen))

            assertEquals("Paused on TV", rig.vm.ui.value!!.notice)
            assertFalse(rig.vm.player.playWhenReady)
            advanceTimeBy(2_001)
            assertNull(rig.vm.ui.value!!.notice)
        }

    @Test
    fun aRemoteNotOnScreenHoldsNoStreamOpenAndCatchesUpOnReturn() =
        runTest {
            val rig = castRemote()
            assertEquals(1, rig.events.subscriptionCount.value)

            rig.vm.screenShown(false)
            runCurrent()
            assertEquals(0, rig.events.subscriptionCount.value, "the phone in a pocket listens to nothing")

            // Paused on the TV meanwhile, and not heard.
            rig.core.togetherMessages[CAST_ID] = writeSession(rig.message().copy(playing = false, pos = 105.0, at = NOW, by = TV.screen))
            rig.vm.screenShown(true)
            runCurrent()

            assertEquals(1, rig.events.subscriptionCount.value)
            assertFalse(rig.vm.player.playWhenReady, "one read on return catches up")
        }

    @Test
    fun theFilmEndingSaysFinished() =
        runTest {
            val rig = castRemote()

            rig.fromTv(finished(rig.message(), TV.screen))

            assertEquals(CastWords.FINISHED, rig.vm.ui.value!!.over)
        }

    @Test
    fun theTvLeavingSaysStopped() =
        runTest {
            val rig = castRemote()

            rig.fromTv(left(rig.message(), TV.screen))

            assertEquals(CastWords.STOPPED, rig.vm.ui.value!!.over)
        }

    @Test
    fun aReadWithTheSessionGoneClosesWithStopped() =
        runTest {
            val rig = castRemote()

            rig.core.togetherMessages.remove(CAST_ID)
            rig.live.refresh()
            runCurrent()

            assertEquals(CastWords.STOPPED, rig.vm.ui.value!!.over)
        }

    @Test
    fun aReadWithAChangedSessionIsApplied() =
        runTest {
            val rig = castRemote()

            val paused = rig.message().copy(playing = false, pos = 50.0, at = NOW, by = TV.screen)
            rig.core.togetherMessages[CAST_ID] = writeSession(paused)
            rig.live.refresh()
            runCurrent()

            assertFalse(rig.vm.player.playWhenReady)
            assertEquals(50_000L, rig.vm.player.currentPosition)
        }

    @Test
    fun aMoveTheChannelNoLongerHasForgetsTheSession() =
        runTest {
            val rig = castRemote()

            rig.core.togetherMessages.remove(CAST_ID)
            rig.vm.player.pause()
            runCurrent()

            assertNull(rig.live.live.value)
            assertEquals(CastWords.STOPPED, rig.vm.ui.value!!.over)
        }

    @Test
    fun aMoveGivenUpSaysTelegramIsntReachable() =
        runTest {
            val rig = castRemote()
            rig.core.togetherFailure = IllegalStateException("offline")

            rig.vm.player.pause()
            runCurrent()
            assertNull(rig.vm.ui.value!!.notice, "reachable when pressed")
            // Three tries, thirty seconds apart: the third fails at 60 s and the move is given up.
            advanceTimeBy(60_001)

            assertEquals(TOGETHER_UNREACHABLE, rig.vm.ui.value!!.notice)
            assertTrue(rig.vm.player.playWhenReady, "back to what the channel holds")
        }

    @Test
    fun aMoveWhileTelegramIsOutOfReachSaysSoAtOnce() =
        runTest {
            val rig = castRemote()
            rig.core.togetherFailure = IllegalStateException("offline")
            rig.live.refresh()

            rig.vm.player.pause()
            runCurrent()

            assertEquals(TOGETHER_UNREACHABLE, rig.vm.ui.value!!.notice)
        }

    @Test
    fun overBeforeAnythingIsDrawnStillReachesTheScreen() =
        runTest {
            val rig = CastRemoteRig(this)
            rig.core.togetherMessages.remove(CAST_ID)

            rig.vm.open(CAST_ID) { emptyList() }
            runCurrent()

            assertEquals(CastWords.STOPPED, rig.vm.ui.value!!.over)
        }

    @Test
    fun aSetChangeRedrawsTheTitle() =
        runTest {
            val rig = castRemote()

            rig.fromTv(rig.message().copy(set = EP2, pos = 0.0, at = NOW, by = TV.screen))

            val ui = rig.vm.ui.value!!
            assertEquals("Second", ui.title)
            assertEquals("/p/ep2.jpg", ui.backdrop)
            assertFalse(ui.hasNext)
            assertEquals("Next episode on TV", ui.notice)
        }

    @Test
    fun aSetMissingFromThisCatalogHasNoTitleAndNoNext() =
        runTest {
            val rig = castRemote()

            rig.fromTv(rig.message().copy(set = HIDDEN, pos = 0.0, at = NOW, by = TV.screen))

            assertEquals("", rig.vm.ui.value!!.title)
            assertFalse(rig.vm.ui.value!!.hasNext)
        }
}

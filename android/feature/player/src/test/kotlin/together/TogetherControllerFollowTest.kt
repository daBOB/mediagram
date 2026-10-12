package player.together

import androidx.media3.common.Player
import io.mockk.verify
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import model.together.PlaybackChange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A screen in a session following what the others do, and never sending what it follows. */
class TogetherControllerFollowTest {
    private val threeHoursMs = 3 * 60 * 60 * 1000L

    @Test
    fun anotherScreensPauseIsFollowedAndSaidForTwoSeconds() =
        runTest {
            val rig = TogetherRig(this).joined()
            val sent = rig.core.togetherEdits.size

            rig.push(950, rig.message(950).copy(playing = false, at = rig.now(), by = TV.screen))

            assertTrue(rig.handle.pauseCalled)
            assertEquals("Paused by TV", rig.controller.ui.value.notice)
            assertEquals(sent, rig.core.togetherEdits.size)
            advanceTimeBy(NOTICE_MS)
            runCurrent()
            assertNull(rig.controller.ui.value.notice)
        }

    @Test
    fun thisScreensOwnMoveComingBackMovesNothing() =
        runTest {
            val rig = TogetherRig(this).joined()

            // Sought to 100 s: the player went there itself, through the viewer's own control.
            rig.controller.viewer(PlaybackChange(pos = 100.0))
            advanceTimeBy(SEEK_SETTLE_MS)
            runCurrent()
            rig.push(950, rig.message(950))

            verify(exactly = 0) { rig.player.seekTo(any<Long>()) }
            assertNull(rig.controller.ui.value.notice)
        }

    @Test
    fun aScreenFallenBehindIsBroughtBackOnTheNextCheck() =
        runTest {
            val rig = TogetherRig(this).joined()
            rig.handle.fakePositionMs = 12_000L - 600

            advanceTimeBy(DRIFT_CHECK_MS)
            runCurrent()

            verify { rig.player.seekTo(12_000L) }
        }

    @Test
    fun aBufferingScreenIsLeftToCatchUpOnALaterCheck() =
        runTest {
            val rig = TogetherRig(this).joined()
            rig.state = Player.STATE_BUFFERING
            rig.handle.fakePositionMs = 5_000L

            advanceTimeBy(DRIFT_CHECK_MS * 3)
            runCurrent()
            verify(exactly = 0) { rig.player.seekTo(any<Long>()) }

            rig.state = Player.STATE_READY
            advanceTimeBy(DRIFT_CHECK_MS)
            runCurrent()
            verify { rig.player.seekTo(18_000L) }
        }

    @Test
    fun aSpeedMedia3HoldsAsAFloatIsTheSessionsOwn() =
        runTest {
            val rig = TogetherRig(this).joined()
            rig.speed = 1.1f

            rig.push(950, rig.message(950).copy(rate = 1.1, at = rig.now(), by = TV.screen))

            assertEquals("1.1× by TV", rig.controller.ui.value.notice)
            assertTrue(rig.handle.speedCalls.isEmpty())
        }

    @Test
    fun atATitlesEndTheScreenWaitsForUpNextButFollowsASeekBack() =
        runTest {
            val rig = TogetherRig(this).joined()
            rig.state = Player.STATE_ENDED
            rig.handle.fakePositionMs = 1_500_000L

            // The TV plays on past the end; going there, or playing, would start this title again.
            rig.push(950, rig.message(950).copy(pos = 1_520.0, at = rig.now(), by = TV.screen))
            advanceTimeBy(DRIFT_CHECK_MS)
            runCurrent()
            verify(exactly = 0) { rig.player.seekTo(any<Long>()) }
            assertFalse(rig.handle.playCalled)

            rig.push(950, rig.message(950).copy(pos = 30.0, at = rig.now(), by = TV.screen))
            verify { rig.player.seekTo(30_000L) }
            assertTrue(rig.handle.playCalled)
        }

    @Test
    fun aSessionEndedElsewhereEndsHereAndPlaybackGoesOn() =
        runTest {
            val rig = TogetherRig(this).joined()

            rig.push(950, rig.message(950).copy(ended = true, by = TV.screen))

            assertEquals(ENDED, rig.controller.ui.value.notice)
            assertEquals(TogetherButton.START, rig.controller.ui.value.button)
            assertFalse(rig.handle.pauseCalled)
        }

    @Test
    fun aSessionGoneStaleUnderThisScreenIsOverAndDeleted() =
        runTest {
            val rig = TogetherRig(this)
            rig.put(950, TV_SESSION.copy(playing = false, at = NOW - threeHoursMs + 1_000))
            rig.openAndPress()
            assertEquals(TogetherButton.LEAVE, rig.controller.ui.value.button)

            advanceTimeBy(DRIFT_CHECK_MS)
            runCurrent()

            assertEquals(ENDED, rig.controller.ui.value.notice)
            assertEquals(listOf(950), rig.core.togetherDeletes)
        }

    @Test
    fun aReadThatFindsANewerSessionLeavesThisScreensOwnRatherThanStayListedInIt() =
        runTest {
            val rig = TogetherRig(this).joined()
            rig.put(960, TV_SESSION.copy(session = "s-2"))

            rig.live.refresh()
            runCurrent()

            assertEquals(ENDED, rig.controller.ui.value.notice)
            assertEquals(TogetherButton.JOIN, rig.controller.ui.value.button)
            assertEquals(listOf(TV.screen), rig.message(950).members.map { it.screen })
        }

    @Test
    fun aNewerSessionHeardByPushLeavesThisScreenInItsOwn() =
        runTest {
            val rig = TogetherRig(this).joined()

            rig.push(960, TV_SESSION.copy(session = "s-2"))

            assertNull(rig.controller.ui.value.notice)
            assertEquals(TogetherButton.LEAVE, rig.controller.ui.value.button)
        }
}

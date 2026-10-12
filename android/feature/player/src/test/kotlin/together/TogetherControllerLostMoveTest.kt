package player.together

import data.TOGETHER_UNREACHABLE
import io.mockk.every
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import model.together.PlaybackChange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A screen's move whose edit throws, from the player to the channel: the
 * controller over the real `DefaultTogetherLive`. The web's twin is
 * `web/test/together-lost-move.test.ts`.
 */
class TogetherControllerLostMoveTest {
    private val retryMs = 30_000L

    /** The viewer's pause, as the player hands it over: the player itself has stopped. */
    private fun TogetherRig.pause() {
        every { player.playWhenReady } returns false
        controller.viewer(PlaybackChange(playing = false))
    }

    @Test
    fun aPauseWhoseEditThrowsIsTriedAgainIn30sNeverSoonerAndLandsOnceTelegramIsBack() =
        runTest {
            val rig = TogetherRig(this).joined()
            assertEquals(listOf(TV.screen, rig.controller.screen), rig.message(950).members.map { it.screen })
            val sent = rig.core.togetherEdits.size
            rig.core.togetherFailure = IllegalStateException("offline")
            rig.pause()
            runCurrent()
            assertEquals(sent + 1, rig.core.togetherEdits.size)
            rig.core.togetherFailure = null

            advanceTimeBy(retryMs - 1)
            runCurrent()
            assertEquals(sent + 1, rig.core.togetherEdits.size)
            advanceTimeBy(1)
            runCurrent()

            assertEquals(sent + 2, rig.core.togetherEdits.size)
            assertEquals(false to rig.controller.screen, rig.message(950).let { it.playing to it.by })
            assertFalse(rig.handle.playCalled)
        }

    @Test
    fun aPauseGivenUpAfterThreeFailedEditsTheScreenThatPausedFollowsWhatTheChannelHoldsAgain() =
        runTest {
            val rig = TogetherRig(this).joined()
            val sent = rig.core.togetherEdits.size
            rig.core.togetherFailure = IllegalStateException("offline")
            rig.pause()
            runCurrent()
            advanceTimeBy(2 * retryMs)
            runCurrent()

            assertEquals(sent + 3, rig.core.togetherEdits.size)
            // The channel still holds the join, written by this very screen: its own echo, which it follows all the same.
            val held = rig.message(950)
            assertEquals(true to rig.controller.screen, held.playing to held.by)
            assertEquals(held, rig.controller.current)
            assertTrue(rig.handle.playCalled)
            advanceTimeBy(retryMs)
            runCurrent()
            assertEquals(sent + 3, rig.core.togetherEdits.size)
        }

    @Test
    fun aMoveWhoseEditThrowsSaysTelegramIsntReachable() =
        runTest {
            val rig = TogetherRig(this).joined()
            rig.core.togetherFailure = IllegalStateException("offline")

            rig.controller.viewer(PlaybackChange(playing = false))
            runCurrent()

            assertEquals(TOGETHER_UNREACHABLE, rig.controller.ui.value.notice)
        }
}

package data

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import model.together.PlaybackChange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A move whose edit throws, as one does while Telegram can't be reached:
 * tried again 30 s later, never sooner, and given up on after three tries,
 * when the screens follow what the channel holds again. The twin of the web
 * hub's "an edit that throws" tests.
 */
class TogetherSenderRetryTest {
    private val pause = PlaybackChange(playing = false, pos = 42.0)
    private val offline = IllegalStateException("offline")

    @Test
    fun anEditThatThrowsIsTriedAgainThirtySecondsLaterNeverSoonerWithTheMovesMadeSinceAndLandsOnceTelegramIsBack() =
        runTest {
            val h = TogetherHarness(this)
            val id = h.start()
            h.core.togetherFailure = offline
            h.live.act(id, A.screen, pause)
            runCurrent()
            assertEquals(1, h.edits.size)
            val sought = h.live.act(id, A.screen, PlaybackChange(pos = 60.0))!!
            h.core.togetherFailure = null

            advanceTimeBy(EDIT_RETRY_MS - 1)
            runCurrent()
            assertEquals(1, h.edits.size)
            advanceTimeBy(1)
            runCurrent()

            assertEquals(2, h.edits.size)
            assertEquals(listOf(false, 60.0), listOf(sought.playing, sought.pos))
            assertEquals(sought, h.message(id))
        }

    @Test
    fun aMoveWhoseEditThrowsThreeTimesIsGivenUpAndWhatTheChannelHoldsIsShownMarkedLost() =
        runTest {
            val h = TogetherHarness(this)
            val id = h.start()
            val held = h.message(id)
            h.core.togetherFailure = offline
            h.live.act(id, A.screen, pause)
            runCurrent()
            advanceTimeBy(2 * EDIT_RETRY_MS)
            runCurrent()

            assertEquals(3, h.edits.size)
            assertEquals(LiveSession(id, held, lost = true), h.live.live.value)
            advanceTimeBy(EDIT_RETRY_MS)
            runCurrent()
            assertEquals(3, h.edits.size)
            // What the channel holds is what the next move builds on.
            val faster = h.live.act(id, A.screen, PlaybackChange(rate = 2.0))!!
            assertEquals(listOf(true, 2.0), listOf(faster.playing, faster.rate))
        }

    @Test
    fun aLastLeaveWhoseEditThrowsIsWrittenAndTheMessageDeletedOnceTelegramIsBack() =
        runTest {
            val h = TogetherHarness(this)
            val id = h.start()
            h.core.togetherFailure = offline
            h.live.leave(id, A.screen)
            runCurrent()
            h.core.togetherFailure = null

            advanceTimeBy(EDIT_RETRY_MS)
            runCurrent()

            assertTrue(h.edits.last().let { it.ended && it.members.isEmpty() })
            assertEquals(listOf(id), h.core.togetherDeletes)
        }

    @Test
    fun aFailedEditIsUnavailableUntilAnEditAnswers() =
        runTest {
            val h = TogetherHarness(this)
            val id = h.start()
            assertTrue(h.live.available.value)
            h.core.togetherFailure = offline
            h.live.act(id, A.screen, pause)
            runCurrent()
            assertFalse(h.live.available.value)

            h.core.togetherFailure = null
            advanceTimeBy(EDIT_RETRY_MS)
            runCurrent()

            assertTrue(h.live.available.value)
        }
}

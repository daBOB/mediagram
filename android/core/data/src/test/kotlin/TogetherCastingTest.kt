package data

import data.CastAnswer.Answer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import model.together.CAST_ANSWER_MS
import model.together.CastState
import model.together.PlaybackChange
import model.together.Session
import model.together.accepted
import model.together.busied
import model.together.refusedBy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The fake core's first message id: where an offer lands. */
private const val OFFER = 900

/** A phone offers a cast and waits for the TV's answer: heard, or read at the deadline. */
class TogetherCastingTest {
    /** [B] offers [FILM], and waits. */
    private fun TestScope.offering(h: TogetherHarness) = async { h.live.offer(B, FILM, "kid", "home") }.also { runCurrent() }

    private fun TestScope.timeOut() = advanceTimeBy(CAST_ANSWER_MS + 1)

    private fun TogetherHarness.accept(): Session = accepted(message(OFFER), TV, NOW + 1_000)!!

    /** The TV answers busy, by push and in the channel, and the offer is answered. */
    private suspend fun TestScope.busyAnswered(h: TogetherHarness) {
        val offer = offering(h)
        val busy = busied(h.message(OFFER), TV.screen, "Dune (2021)")
        h.put(OFFER, busy)
        h.push(OFFER, busy)
        offer.await()
    }

    @Test
    fun anAcceptAnswersTheOffer() =
        runTest {
            val h = TogetherHarness(this)
            val offer = offering(h)
            h.push(OFFER, h.accept())

            assertTrue(offer.isCompleted, "heard, not read at the deadline")
            val answer = offer.await()
            assertEquals(Answer.ON, answer.answer)
            assertEquals("tv-1", answer.live!!.session.cast!!.tv)
        }

    @Test
    fun aBusyTvAnswersWithItsTitle() =
        runTest {
            val h = TogetherHarness(this)
            val offer = offering(h)
            h.push(OFFER, busied(h.message(OFFER), TV.screen, "Dune (2021)"))

            val answer = offer.await()
            assertEquals(Answer.BUSY, answer.answer)
            assertEquals("Dune (2021)", answer.note)
            assertEquals(OFFER, answer.live?.id)
        }

    @Test
    fun aRefusingTvAnswersWithItsReasonAndTheOffersMessage() =
        runTest {
            val h = TogetherHarness(this)
            val offer = offering(h)
            h.push(OFFER, refusedBy(h.message(OFFER), TV.screen, "too young"))

            val answer = offer.await()
            assertEquals(Answer.REFUSED, answer.answer)
            assertEquals("too young", answer.note)
            assertEquals(OFFER, answer.live?.id)
        }

    @Test
    fun offerReadsTheMessageBeforeGivingUp() =
        runTest {
            val h = TogetherHarness(this)
            val offer = offering(h)
            h.put(OFFER, h.accept())
            timeOut()

            assertEquals(Answer.ON, offer.await().answer)
            assertEquals(emptyList(), h.core.togetherDeletes)
        }

    @Test
    fun anAnswerReadAtTheDeadlineIsWhatTheNextMoveBuildsOn() =
        runTest {
            val h = TogetherHarness(this)
            val offer = offering(h)
            h.put(OFFER, h.accept())
            timeOut()
            offer.await()

            val moved = h.live.act(OFFER, B.screen, PlaybackChange(playing = true))
            runCurrent()

            assertEquals(CastState.ON, moved?.cast?.state)
            assertTrue(moved!!.members.any { it.screen == TV.screen })
            assertEquals(CastState.ON, h.message(OFFER).cast?.state)
        }

    @Test
    fun noAnswerDeletesTheOffer() =
        runTest {
            val h = TogetherHarness(this)
            val offer = offering(h)
            timeOut()

            assertEquals(CastAnswer(Answer.NONE, null, ""), offer.await())
            assertEquals(listOf(OFFER), h.core.togetherDeletes)
            assertEquals(emptyList(), h.live.requests.value)
        }

    @Test
    fun aFailedReadAtTheDeadlineStillWithdrawsTheOffer() =
        runTest {
            val h = TogetherHarness(this)
            val offer = offering(h)
            h.core.togetherReadFailure = IllegalStateException("Telegram can't be reached")
            timeOut()

            assertEquals(Answer.NONE, offer.await().answer)
            assertEquals(listOf(OFFER), h.core.togetherDeletes)
            assertEquals(emptyList(), h.live.requests.value)
        }

    @Test
    fun anAnswerHeardBeforeTheSendReturnsIsKeptOverTheOffer() =
        runTest {
            val h = TogetherHarness(this)
            val sent = CompletableDeferred<Unit>()
            h.core.togetherSendGate = sent
            val offer = offering(h)
            h.push(OFFER, h.accept())
            sent.complete(Unit)
            runCurrent()

            assertTrue(offer.isCompleted, "heard while sending, not read at the deadline")
            assertEquals(Answer.ON, offer.await().answer)
            assertEquals(emptyList(), h.live.requests.value)
            assertEquals(CastState.ON, h.live.act(OFFER, B.screen, PlaybackChange(playing = true))?.cast?.state)
        }

    @Test
    fun replaceAfterStaleOffersAnew() =
        runTest {
            val h = TogetherHarness(this)
            busyAnswered(h)
            h.clock += 61_000
            val again = async { h.live.replace(OFFER, B, FILM.copy(pos = 40.0), "kid", "home") }
            runCurrent()

            assertEquals(CastState.OFFER, h.message(OFFER + 1).cast?.state)
            assertEquals(emptyList(), h.edits)
            timeOut()
            assertEquals(Answer.NONE, again.await().answer)
        }

    @Test
    fun replaceOnALiveBusyRequestAsksTheTvToSwitch() =
        runTest {
            val h = TogetherHarness(this)
            busyAnswered(h)
            h.clock += 2_000
            val again = async { h.live.replace(OFFER, B, FILM.copy(pos = 40.0), "kid", "home") }
            runCurrent()

            val asked = h.edits.single()
            val written = listOf(asked.cast?.state, asked.pos, asked.at, asked.by)
            assertEquals(listOf(CastState.REPLACE, 40.0, NOW + 2_000, B.screen), written)
            h.push(OFFER, accepted(asked, TV, NOW + 3_000)!!)
            val answer = again.await()
            assertEquals(Answer.ON, answer.answer)
            assertEquals(OFFER, answer.live?.id)
            assertEquals(OFFER + 1, h.core.nextTogetherId, "nothing offered anew")
        }

    @Test
    fun cancelDeletesTheOffer() =
        runTest {
            val h = TogetherHarness(this)
            busyAnswered(h)
            h.live.cancel(OFFER)
            runCurrent()

            assertEquals(listOf(OFFER), h.core.togetherDeletes)
            assertEquals(emptyList(), h.live.requests.value)
        }
}

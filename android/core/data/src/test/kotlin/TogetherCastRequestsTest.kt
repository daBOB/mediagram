package data

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import model.together.CastState
import model.together.REQUEST_LIFETIME_MS
import model.together.Session
import model.together.offered
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A cast request is heard beside the session screens show, never instead of it. */
class TogetherCastRequestsTest {
    private val offer: Session = offered("s-offer", A, FILM, "kid", "home", NOW)

    @Test
    fun requestsAreNeverShown() =
        runTest {
            val h = TogetherHarness(this)
            h.watch()
            h.push(10, TV_SESSION)
            h.push(11, offer)

            assertEquals(10, h.live.live.value?.id)
            assertEquals(listOf(11), h.live.requests.value.map { it.id })
        }

    @Test
    fun eachHouseholdKeepsItsNewestOffer() =
        runTest {
            val h = TogetherHarness(this)
            h.watch()
            h.push(11, offer)
            h.push(12, offered("s-away", A, FILM, "kid", "h2", NOW))
            h.push(13, offered("s-again", A, FILM, "kid", "home", NOW))

            // Another household's, landing in between, hides neither of this one's: the newer stands.
            assertEquals(listOf(12, 13), h.live.requests.value.map { it.id })
        }

    @Test
    fun aRequestGoesAfterAMinute() =
        runTest {
            val h = TogetherHarness(this)
            backgroundScope.launch { h.live.requests.collect {} }
            runCurrent()
            h.put(11, offer)
            h.push(11, offer)
            assertEquals(listOf(11), h.live.requests.value.map { it.id })

            h.clock = NOW + REQUEST_LIFETIME_MS
            advanceTimeBy(30_001)
            runCurrent()

            assertEquals(emptyList(), h.live.requests.value)
            assertEquals(listOf(11), h.core.togetherDeletes)
        }

    @Test
    fun collectingRequestsAloneListens() =
        runTest {
            val h = TogetherHarness(this)
            backgroundScope.launch { h.live.requests.collect {} }
            runCurrent()
            h.push(11, offer)

            assertEquals(listOf(11), h.live.requests.value.map { it.id })
            assertNull(h.live.live.value)
        }

    @Test
    fun discoveryFindsAnOfferBesideASession() =
        runTest {
            val h = TogetherHarness(this)
            h.put(10, TV_SESSION)
            h.put(11, offer)

            val found = h.live.refresh()

            assertEquals(10, found?.id)
            assertEquals(10, h.live.live.value?.id)
            assertEquals(listOf(11), h.live.requests.value.map { it.id })
        }

    private val accepted: Session = offer.copy(cast = offer.cast?.copy(state = CastState.ON))

    private fun TestScope.pastTheMinute(h: TogetherHarness) {
        h.clock = NOW + REQUEST_LIFETIME_MS
        advanceTimeBy(30_001)
        runCurrent()
    }

    @Test
    fun anAcceptLearnedOnlyByRefreshIsNotDeleted() =
        runTest {
            val h = TogetherHarness(this)
            backgroundScope.launch { h.live.requests.collect {} }
            runCurrent()
            h.push(11, offer)
            h.put(11, accepted)

            h.live.refresh()
            pastTheMinute(h)

            assertEquals(11, h.live.live.value?.id)
            assertEquals(emptyList(), h.live.requests.value)
            assertEquals(emptyList(), h.core.togetherDeletes)
        }

    @Test
    fun anAcceptMissedAltogetherIsNotDeleted() =
        runTest {
            val h = TogetherHarness(this)
            backgroundScope.launch { h.live.requests.collect {} }
            runCurrent()
            h.push(11, offer)
            h.put(11, accepted)

            pastTheMinute(h)

            assertEquals(emptyList(), h.core.togetherDeletes)
            assertEquals(emptyList(), h.live.requests.value)
            assertEquals(11, h.live.live.value?.id)
        }

    @Test
    fun anOfferBesideASessionThisDeviceIsInLeavesItAlone() =
        runTest {
            val h = TogetherHarness(this)
            h.watch()
            h.live.start(A, FILM)
            val mine = h.core.togetherMessages.keys.single()
            val before = h.edits.size
            h.push(mine + 1, offer)

            assertEquals(mine, h.live.live.value?.id)
            assertEquals(listOf(mine + 1), h.live.requests.value.map { it.id })
            assertEquals(before, h.edits.size)
        }

    @Test
    fun anAcceptByPushMovesTheRequestToLive() =
        runTest {
            val h = TogetherHarness(this)
            h.watch()
            h.push(11, offer)
            h.push(11, accepted)

            assertEquals(emptyList(), h.live.requests.value)
            assertEquals(11, h.live.live.value?.id)
        }
}

package data

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import model.together.REQUEST_LIFETIME_MS
import model.together.Session
import model.together.offered
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A cast request is heard beside the session screens show, never instead of it. */
class TogetherCastRequestsTest {
    private val offer: Session = offered("s-offer", A, FILM, "kid", NOW)

    @Test
    fun requestsAreNeverShown() =
        runTest {
            val h = TogetherHarness(this)
            h.watch()
            h.push(10, TV_SESSION)
            h.push(11, offer)

            assertEquals(10, h.live.live.value?.id)
            assertEquals(11, h.live.requests.value?.id)
        }

    @Test
    fun aRequestGoesAfterAMinute() =
        runTest {
            val h = TogetherHarness(this)
            backgroundScope.launch { h.live.requests.collect {} }
            runCurrent()
            h.push(11, offer)
            assertEquals(11, h.live.requests.value?.id)

            h.clock = NOW + REQUEST_LIFETIME_MS
            advanceTimeBy(30_001)
            runCurrent()

            assertNull(h.live.requests.value)
            assertEquals(listOf(11), h.core.togetherDeletes)
        }

    @Test
    fun collectingRequestsAloneListens() =
        runTest {
            val h = TogetherHarness(this)
            backgroundScope.launch { h.live.requests.collect {} }
            runCurrent()
            h.push(11, offer)

            assertEquals(11, h.live.requests.value?.id)
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
            assertEquals(11, h.live.requests.value?.id)
        }
}

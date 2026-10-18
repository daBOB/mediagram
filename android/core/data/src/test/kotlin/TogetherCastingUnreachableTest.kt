package data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** A cast move that can't reach Telegram throws, so the screen can say so, and leaves nothing behind. */
class TogetherCastingUnreachableTest {
    // Coroutines rethrow a copy that carries the caller's stack: the message is what is compared.
    private val unreachable = IllegalStateException("Telegram can't be reached")

    @Test
    fun aFailedSendThrowsAndLeavesNothing() =
        runTest {
            val h = TogetherHarness(this)
            h.core.togetherFailure = unreachable

            val thrown = assertFailsWith<IllegalStateException> { h.live.offer(B, FILM, "kid") }
            assertEquals(unreachable.message, thrown.message)
            assertEquals(emptyList(), h.core.togetherDeletes)
            assertEquals(emptyMap(), h.core.togetherMessages)
            assertNull(h.live.requests.value)
        }

    @Test
    fun aFailedReadBeforeReplacingThrowsAndSendsNothing() =
        runTest {
            val h = TogetherHarness(this)
            h.core.togetherFailure = unreachable

            val thrown = assertFailsWith<IllegalStateException> { h.live.replace(900, B, FILM, "kid") }
            assertEquals(unreachable.message, thrown.message)
            assertEquals(emptyMap(), h.core.togetherMessages)
            assertEquals(emptyList(), h.edits)
            assertEquals(emptyList(), h.core.togetherDeletes)
        }
}

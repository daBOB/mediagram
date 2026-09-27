package playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun film(id: String, bytes: Long = 100L) = PreloadItem(id, "Film $id", bytes)

class FilmPreloadQueueTest {

    @Test
    fun aFreshQueueIsEmptyAndHasNothingActive() {
        val queue = FilmPreloadQueue()

        assertTrue(queue.isEmpty)
        assertNull(queue.activeId)
    }

    @Test
    fun enqueueAddsToTheBackAndNextToRunPopsFromTheFront() {
        val queue = FilmPreloadQueue()

        assertTrue(queue.enqueue(film("f1")))
        assertTrue(queue.enqueue(film("f2")))

        assertEquals("f1", queue.nextToRun()?.setId)
        assertEquals("f1", queue.activeId)
    }

    @Test
    fun enqueueIsIdempotentForAQueuedItem() {
        val queue = FilmPreloadQueue()
        queue.enqueue(film("f1"))

        assertFalse(queue.enqueue(film("f1")))
    }

    @Test
    fun enqueueIsIdempotentForTheActiveItem() {
        val queue = FilmPreloadQueue()
        queue.enqueue(film("f1"))
        queue.nextToRun()

        assertFalse(queue.enqueue(film("f1")))
        assertFalse(queue.isEmpty)
    }

    @Test
    fun nextToRunAnswersNullWhileSomethingIsAlreadyActive() {
        val queue = FilmPreloadQueue()
        queue.enqueue(film("f1"))
        queue.enqueue(film("f2"))
        queue.nextToRun()

        assertNull(queue.nextToRun())
    }

    @Test
    fun clearActiveLetsTheNextItemRun() {
        val queue = FilmPreloadQueue()
        queue.enqueue(film("f1"))
        queue.enqueue(film("f2"))
        queue.nextToRun()
        queue.clearActive()

        assertEquals("f2", queue.nextToRun()?.setId)
    }

    @Test
    fun cancelOnAQueuedItemDropsItWithoutTouchingActive() {
        val queue = FilmPreloadQueue()
        queue.enqueue(film("f1"))
        queue.enqueue(film("f2"))
        queue.nextToRun() // f1 active

        val cancelled = queue.cancel("f2")

        assertNull(cancelled)
        assertEquals("f1", queue.activeId)
    }

    @Test
    fun cancelOnTheActiveItemReturnsItAndClearsActive() {
        val queue = FilmPreloadQueue()
        queue.enqueue(film("f1", bytes = 500L))
        queue.nextToRun()

        val cancelled = queue.cancel("f1")

        assertEquals("f1", cancelled?.setId)
        assertEquals(500L, cancelled?.totalBytes)
        assertNull(queue.activeId)
    }

    @Test
    fun cancelOnAnUnknownIdAnswersNull() {
        val queue = FilmPreloadQueue()

        assertNull(queue.cancel("nowhere"))
    }
}

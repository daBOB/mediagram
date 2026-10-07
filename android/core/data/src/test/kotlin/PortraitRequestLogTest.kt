package data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortraitRequestLogTest {
    @Test
    fun aPersonNeedsAFetchUntilOneFinishes() {
        val log = PortraitRequestLog()
        assertTrue(log.needsFetch(1))
        assertTrue(log.needsFetch(1), "asking is not reserving")
        log.finish(1, "portrait.jpg")
        assertFalse(log.needsFetch(1))
        assertTrue(log.needsFetch(2))
    }

    @Test
    fun aFoundPortraitIsKeptForTheNextCard() {
        val log = PortraitRequestLog()
        log.finish(1, "portrait.jpg")
        assertEquals("portrait.jpg", log.pathOf(1))
    }

    @Test
    fun aFetchThatFoundNothingStillFinishes() {
        val log = PortraitRequestLog()
        log.finish(1, null)
        assertFalse(log.needsFetch(1))
        assertNull(log.pathOf(1))
    }

    @Test
    fun aFreshLogStartsClean() {
        PortraitRequestLog().finish(1, "portrait.jpg")
        val log = PortraitRequestLog()
        assertTrue(log.needsFetch(1))
        assertNull(log.pathOf(1))
    }
}

package data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PortraitRequestLogTest {
    @Test
    fun asksOncePerPersonThenRefuses() {
        val log = PortraitRequestLog()
        assertTrue(log.shouldRequest(1))
        assertFalse(log.shouldRequest(1))
        assertTrue(log.shouldRequest(2))
    }
}

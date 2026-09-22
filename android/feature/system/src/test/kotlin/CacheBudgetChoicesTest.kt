package system

import playback.CACHE_MAX_BYTES
import playback.MIN_CACHE_BYTES
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CacheBudgetChoicesTest {

    @Test
    fun theChoicesRunFromTheFloorAndIncludeTheDefault() {
        val choices = cacheBudgetChoices()
        assertEquals(MIN_CACHE_BYTES, choices.first())
        assertTrue(CACHE_MAX_BYTES in choices, "the default must be one of the choices, or it could not be chosen back")
        assertEquals(choices.sorted(), choices)
        assertEquals(choices.distinct(), choices)
    }
}

package ui

import kotlin.test.Test
import kotlin.test.assertEquals

/** What a double-tap seek leaves for [SeekRipple] to show. */
class SeekRippleTest {

    @Test
    fun aFirstDoubleTapShowsExactlyTheIncrement() {
        val flash = accumulateFlash(previous = null, zone = SeekZone.BACK, incrementSeconds = 10, nowMs = 0L)
        assertEquals(SeekFlash(SeekZone.BACK, 10, 0L), flash)
    }

    @Test
    fun aSecondDoubleTapOnTheSameSideSoonEnoughAdds() {
        val first = accumulateFlash(previous = null, zone = SeekZone.FORWARD, incrementSeconds = 10, nowMs = 0L)
        val second = accumulateFlash(first, zone = SeekZone.FORWARD, incrementSeconds = 10, nowMs = 500L)
        assertEquals(20L, second.totalSeconds)
        assertEquals(500L, second.atMs)
    }

    @Test
    fun switchingSidesStartsOverRatherThanAdding() {
        val back = accumulateFlash(previous = null, zone = SeekZone.BACK, incrementSeconds = 10, nowMs = 0L)
        val forward = accumulateFlash(back, zone = SeekZone.FORWARD, incrementSeconds = 10, nowMs = 200L)
        assertEquals(SeekFlash(SeekZone.FORWARD, 10, 200L), forward)
    }

    @Test
    fun aDoubleTapAfterTheAccumulateWindowStartsOverToo() {
        val first = accumulateFlash(previous = null, zone = SeekZone.BACK, incrementSeconds = 10, nowMs = 0L)
        val late = accumulateFlash(first, zone = SeekZone.BACK, incrementSeconds = 10, nowMs = SEEK_FLASH_ACCUMULATE_MS + 1)
        assertEquals(10L, late.totalSeconds)
    }

    @Test
    fun theLabelIsSignedByDirection() {
        assertEquals("−10 s", seekFlashLabel(SeekFlash(SeekZone.BACK, 10, 0L)))
        assertEquals("+20 s", seekFlashLabel(SeekFlash(SeekZone.FORWARD, 20, 0L)))
    }
}

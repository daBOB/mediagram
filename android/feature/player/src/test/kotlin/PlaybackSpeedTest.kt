package player

import kotlin.test.Test
import kotlin.test.assertEquals

/** Covers [speedLabel] and [speedOrDefault] — ported from `web/test/transport.test.ts`. */
class PlaybackSpeedTest {

    @Test
    fun theSpeedOnTheMenuDropsTheNoiseAfterThePoint() {
        assertEquals("1×", speedLabel(1f))
        assertEquals("1.5×", speedLabel(1.5f))
        assertEquals("0.75×", speedLabel(0.75f))
    }

    @Test
    fun aRateThatIsNotOneFallsBackToOne() {
        for (rate in listOf(0f, -1f, Float.NaN)) {
            assertEquals("1×", speedLabel(rate))
        }
    }

    @Test
    fun aRememberedSpeedFromTheSixOffersIsTrusted() {
        assertEquals(1.5f, speedOrDefault("1.5"))
        assertEquals(0.75f, speedOrDefault("0.75"))
    }

    @Test
    fun anythingNotAmongTheSixOffersFallsBackToOne() {
        assertEquals(1f, speedOrDefault(null))
        assertEquals(1f, speedOrDefault(""))
        assertEquals(1f, speedOrDefault("fast"))
        // 3x was never on the menu; a stale or foreign value is not trusted.
        assertEquals(1f, speedOrDefault("3"))
    }

    @Test
    fun theStoredValueIsTheSameStringTheWebWrites() {
        assertEquals("1.5", speedPreferenceValue(1.5f))
        assertEquals("1", speedPreferenceValue(1f))
        assertEquals("0.75", speedPreferenceValue(0.75f))
    }
}

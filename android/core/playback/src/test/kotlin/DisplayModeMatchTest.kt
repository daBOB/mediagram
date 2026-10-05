package playback

import androidx.media3.common.Format
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DisplayModeMatchTest {
    private fun mode(
        id: Int,
        hz: Float,
        width: Int = 3840,
        height: Int = 2160,
    ) = Mode(id, width, height, hz)

    private val at5994 = mode(1, 59.94f)

    @Test
    fun filmRateFindsItsOwnMode() {
        assertEquals(2, pickDisplayMode(at5994, listOf(at5994, mode(2, 23.976f)), 23.976f))
    }

    @Test
    fun exactTwentyFourBeatsItsMultiples() {
        val modes = listOf(mode(1, 60f), mode(2, 24f), mode(3, 48f))
        assertEquals(2, pickDisplayMode(modes[0], modes, 24f))
    }

    @Test
    fun twentyFiveTakesFiftyWhenThereIsNoTwentyFive() {
        val modes = listOf(mode(1, 60f), mode(2, 50f))
        assertEquals(2, pickDisplayMode(modes[0], modes, 25f))
    }

    @Test
    fun alreadyOnTheBestModeIsNoChange() {
        assertNull(pickDisplayMode(at5994, listOf(at5994), 29.97f))
    }

    @Test
    fun noMultipleOnOfferIsNoChange() {
        val modes = listOf(at5994, mode(2, 60f))
        assertNull(pickDisplayMode(at5994, modes, 23.976f))
    }

    @Test
    fun anotherResolutionIsNeverChosen() {
        val modes = listOf(at5994, mode(2, 24f, width = 1920, height = 1080))
        assertNull(pickDisplayMode(at5994, modes, 24f))
    }

    @Test
    fun unknownRateIsNoChange() {
        val modes = listOf(at5994, mode(2, 24f))
        assertNull(pickDisplayMode(at5994, modes, 0f))
        assertNull(pickDisplayMode(at5994, modes, Format.NO_VALUE.toFloat()))
    }
}

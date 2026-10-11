package ui.tv.catalog

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** A landscape row's arithmetic and its choice of picture. */
class TvRowCardsTest {
    @Test
    fun fiveStillsShareTheRowLessFourGaps() {
        assertEquals((864.dp - StripGap * 4) / 5, landscapeStripPlateWidth(864.dp))
    }

    @Test
    fun aNarrowOrUnboundedRowTakesTheFloor() {
        assertEquals(LandscapeStripPlateFloor, landscapeStripPlateWidth(500.dp))
        assertEquals(LandscapeStripPlateFloor, landscapeStripPlateWidth(Dp.Infinity))
        assertEquals(StripPlateFloor * 1.5f, LandscapeStripPlateFloor)
    }

    @Test
    fun aLandscapeRowShowsTheBackdropThenThePosterAPortraitRowThePoster() {
        assertEquals("b", rowArtOf(landscape = true, poster = "p", backdrop = "b"))
        assertEquals("p", rowArtOf(landscape = true, poster = "p", backdrop = null))
        assertEquals("p", rowArtOf(landscape = false, poster = "p", backdrop = "b"))
    }
}

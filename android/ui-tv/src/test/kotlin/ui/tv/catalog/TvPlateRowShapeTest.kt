package ui.tv.catalog

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import catalog.initialsOf
import designsystem.TvStyle
import kotlin.math.abs
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A plate in a row is a 16:9 still under Apple and Google and a poster under mediagram; a plate on a wall is a poster in every style. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvPlateRowShapeTest : TvScreenStateTest() {
    private fun ratio(): Float {
        val art = compose.onNodeWithTag(PlateArtTag, useUnmergedTree = true).getUnclippedBoundsInRoot()
        return (art.right - art.left) / (art.bottom - art.top)
    }

    @androidx.compose.runtime.Composable
    private fun Plate(inRow: Boolean) = TvPlate(title = "Die Hard", posterPath = null, onOpen = {}, modifier = Modifier.width(160.dp), inRow = inRow)

    @Test
    fun aRowPlateIsAStillUnderAppleAndGoogle() {
        for (style in listOf(TvStyle.APPLE, TvStyle.GOOGLE)) {
            show(style) { Plate(inRow = true) }
            assertTrue(abs(ratio() - 16f / 9f) < 0.02f, "$style: ${ratio()}")
            close()
        }
    }

    @Test
    fun aRowPlateStaysAPosterUnderMediagram() {
        show(TvStyle.MEDIAGRAM) { Plate(inRow = true) }
        assertTrue(abs(ratio() - 2f / 3f) < 0.02f, "${ratio()}")
    }

    @Test
    fun aWallPlateStaysAPosterInEveryStyle() {
        show(TvStyle.APPLE) { Plate(inRow = false) }
        assertTrue(abs(ratio() - 2f / 3f) < 0.02f, "${ratio()}")
    }

    /** No backdrop and no poster: the plate's initials, on a still's proportions. */
    @Test
    fun aStillWithNoArtAtAllShowsTheInitials() {
        show(TvStyle.APPLE) { Plate(inRow = true) }
        compose.onNodeWithText(initialsOf("Die Hard")).assertExists()
    }
}

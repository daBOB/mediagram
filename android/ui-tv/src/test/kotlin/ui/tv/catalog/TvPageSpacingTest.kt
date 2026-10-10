package ui.tv.catalog

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import designsystem.TvScale
import model.Kind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * The spacing a catalogue page takes from the web, each the web's px at the
 * TV's 1280px reading through [TvScale], at the box's density.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvPageSpacingTest : TvScreenStateTest() {
    @Test
    fun thePageSpacingIsTheWebsPxThroughTheScale() {
        assertEquals(12 * TvScale, HomeFeaturesTop.value, "`.features` margin-top")
        assertEquals(28 * TvScale, HomeBandTop.value, "`.home-band` margin-top")
        assertEquals(36 * TvScale, HomeSectionTop.value, "`.home-section` margin-top")
        assertEquals(51.2f * TvScale, HomeBandGap.value, 0.001f, "`.home-band` gap, 4vw at 1280px")
        assertEquals(20 * TvScale, RowHeadBelow.value, "`.row-head` margin-bottom")
        assertEquals(44 * TvScale, DeptRowTop.value, "`.dept-row` margin-top")
        assertEquals(16 * TvScale, ResumeGap.value, "`.resume-strip` gap")
        assertEquals(20 * TvScale, ShelfHeadRuleTop.value, "`.shelf-head` padding-bottom")
    }

    @Test
    fun aWallOfPlatesKeepsTheWebsGridGaps() {
        show { TvWall(items = items(Columns + 1), key = WallItem::id, restoreKey = null, onOpen = {}, plate = { item, modifier, _ -> FillPlate(item, modifier) }) }

        val first = bounds("p0")
        assertEquals(22 * TvScale, (bounds("p1").left - first.right).value, OnePixel, "`.grid.plates` 22 across")
        assertEquals(36 * TvScale, (bounds("p$Columns").top - first.bottom).value, OnePixel, "36 between lines")
    }

    @Test
    fun aWallOfTilesKeepsTheTilesGap() {
        show { TvWall(items = items(4), key = WallItem::id, restoreKey = null, onOpen = {}, columns = 3, plate = { item, modifier, _ -> FillPlate(item, modifier) }) }

        val first = bounds("p0")
        assertEquals(14 * TvScale, (bounds("p1").left - first.right).value, OnePixel, "`.genre-tiles` gap across")
        assertEquals(14 * TvScale, (bounds("p3").top - first.bottom).value, OnePixel, "and between lines")
    }

    @Test
    fun aDepartmentRowStandsUnderItsHeadAsTheWebsDoes() {
        show {
            Column {
                DeptRow(title = "Popular", films = (0 until 3).map { set("f$it", Kind.MOVIE, "Film $it", addedAt = 0) }, onOpenTitle = {}, takesFocus = false)
            }
        }
        val head = compose.onNodeWithText("Popular").getUnclippedBoundsInRoot()
        val first = compose.onNodeWithText("Film 0").getUnclippedBoundsInRoot()
        val second = compose.onNodeWithText("Film 1").getUnclippedBoundsInRoot()

        assertEquals(44 * TvScale, head.top.value, OnePixel, "`.dept-row` margin-top")
        assertEquals(20 * TvScale, (first.top - head.bottom).value, OnePixel, "`.row-head` margin-bottom")
        assertEquals(14 * TvScale, (second.left - first.right).value, OnePixel, "`.grid.plates.strip` gap")
    }

    private fun items(count: Int) = (0 until count).map { WallItem("p$it") }

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private companion object {
        /** Layout rounds to whole pixels; at the box's density one is half a dp. */
        const val OnePixel = 0.5f
    }
}

/** A plate that fills its grid cell, so the space between two is the grid's own gap. */
@Composable
private fun FillPlate(
    item: WallItem,
    modifier: Modifier,
) {
    Box(modifier.testTag(item.id).fillMaxWidth().height(80.dp).focusable())
}

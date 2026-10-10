package ui.tv.catalog

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import designsystem.TvScale
import kotlin.test.assertEquals
import model.Kind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ui.tv.catalog.home.TvCoverActions
import ui.tv.chrome.LocalTvPagePadding
import ui.tv.chrome.TvContentGutter
import ui.tv.chrome.TvPagePadding

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
        assertEquals(36 * TvScale, ShelfHeadBelow.value, "`.shelf-head` margin-bottom")
        assertEquals(40 * TvScale, ShelfSubAbove.value, "`.shelf-sub` margin-top")
        assertEquals(20 * TvScale, ShelfSubBelow.value, "`.shelf-sub` margin-bottom")
    }

    /** The grid's one line gap used to stand around the head and a section's heading as well. */
    @Test
    fun aWallsHeadAndHeadingsKeepTheirOwnMargins() {
        show {
            TvWall(
                items = items(Columns * 2), key = WallItem::id, restoreKey = null, onOpen = {},
                header = { TvShelfHead("Genres", "six genres") },
                headings = mapOf(Columns to "Films"),
                plate = { item, modifier, _ -> FillPlate(item, modifier) },
            )
        }
        val title = text("Genres")
        val firstLine = bounds("p0")
        val heading = compose.onNodeWithText("Films").getUnclippedBoundsInRoot()

        // Title, 20 to the 1dp rule, then the head's 36: nothing of the TV's own between them.
        assertEquals((20 + 36) * TvScale + 1, (firstLine.top - title.bottom).value, OnePixel, "`.shelf-head` padding and margin")
        assertEquals(40 * TvScale, (heading.top - firstLine.bottom).value, OnePixel, "`.shelf-sub` margin-top")
        assertEquals(20 * TvScale, (bounds("p$Columns").top - heading.bottom).value, OnePixel, "`.shelf-sub` margin-bottom")
    }

    /** A department's section heads are `.row-head`s inside a `.dept-row`, not `.shelf-sub`s. */
    @Test
    fun aRowHeadHeadingTakesTheDeptRowAndRowHeadMargins() {
        show {
            TvWall(
                items = items(Columns * 2), key = WallItem::id, restoreKey = null, onOpen = {}, headingStyle = RowHead,
                headings = mapOf(Columns to "Films"),
                plate = { item, modifier, _ -> FillPlate(item, modifier) },
            )
        }
        val heading = compose.onNodeWithText("Films").getUnclippedBoundsInRoot()

        assertEquals(44 * TvScale, (heading.top - bounds("p0").bottom).value, OnePixel, "`.dept-row` margin-top")
        assertEquals(20 * TvScale, (bounds("p$Columns").top - heading.bottom).value, OnePixel, "`.row-head` margin-bottom")
    }

    @Test
    fun aWallOfTilesStandsUnderItsHeadByTheHeadsMargin() {
        show {
            TvWall(
                items = items(4), key = WallItem::id, restoreKey = null, onOpen = {}, columns = 3,
                header = { Box(Modifier.testTag(HEAD).fillMaxWidth().height(40.dp)) },
                plate = { item, modifier, _ -> FillPlate(item, modifier) },
            )
        }

        assertEquals(36 * TvScale, (bounds("p0").top - bounds(HEAD).bottom).value, OnePixel, "the head's margin, not the tiles' gap")
    }

    @Test
    fun anArtTileAndAPagePillAreInsetAsTheWebsAre() {
        show {
            Column {
                TvArtTile(name = "Drama", meta = "21 titles", art = null, aspectRatio = 16f / 9f, onOpen = {}, modifier = Modifier.width(300.dp))
                TvArtTile(name = "Bond", meta = "25 films", art = null, aspectRatio = 4f / 3f, onOpen = {}, modifier = Modifier.width(300.dp), destination = true)
                TvPagePill(text = "All 1143 films", onClick = {})
            }
        }
        val genre = compose.onNodeWithText("Drama").getUnclippedBoundsInRoot()
        val genreName = text("Drama")
        val destination = compose.onNodeWithText("BOND").getUnclippedBoundsInRoot()
        val pill = compose.onNodeWithText("All 1143 films").getUnclippedBoundsInRoot()

        assertEquals(18 * TvScale, (genreName.left - genre.left).value, OnePixel, "`.genre-tile` 18 across")
        assertEquals(16 * TvScale, (genre.bottom - text("21 titles").bottom).value, OnePixel, "and 16 below")
        assertEquals(4 * TvScale, (text("21 titles").top - genreName.bottom).value, OnePixel, "`.genre-tile-count` 4")
        assertEquals(22 * TvScale, (text("BOND").left - destination.left).value, OnePixel, "`.destination` 22 across")
        assertEquals(6 * TvScale, (text("25 films").top - text("BOND").bottom).value, OnePixel, "`.destination-meta` 6")
        assertEquals(52 * TvScale, pill.height.value, OnePixel, "`.dept-all` 52 tall")
        assertEquals(28 * TvScale, (text("All 1143 films").left - pill.left).value, OnePixel, "28 in")
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
        assertEquals((960 - 7 * 14 * TvScale) / 8, first.width.value, OnePixel, "an eighth of the screen-wide row, less its gaps")
    }

    /** `.grid.plates.strip`'s `minmax(8.5rem, calc((100% - 7 * 14px) / 8))`, where every row of posters used to be 160dp. */
    @Test
    fun aStripPlateIsAnEighthOfItsRowAndNeverUnderTheFloor() {
        assertEquals(136 * TvScale, StripPlateFloor.value, "8.5rem")
        assertEquals(StripPlateFloor, stripPlateWidth(800.dp), "an 800dp row's eighth is under the floor")
        assertEquals((1000 - 7 * 14 * TvScale) / 8, stripPlateWidth(1000.dp).value, 0.001f, "a wide row's eighth, less seven gaps")
        assertEquals(StripPlateFloor, stripPlateWidth(Dp.Infinity), "a row with no bound")
    }

    @Test
    fun theCoverActionsStandOnThePageEdgeAtTheWebsGap() {
        val page = TvPagePadding(start = TvContentGutter, top = 0.dp, end = TvContentGutter, bottom = 0.dp)
        show {
            CompositionLocalProvider(LocalTvPagePadding provides page) {
                TvCoverActions(
                    title = "Heat", watchlisted = false, dotCount = 1, current = 0, onPlay = {}, onToggleWatchlist = {}, onDetails = {}, onFocusDot = {},
                    watchNowFocus = remember { FocusRequester() }, upExit = remember { FocusRequester() },
                )
            }
        }
        val watch = compose.onNodeWithContentDescription("Watch now: Heat").getUnclippedBoundsInRoot()
        val list = compose.onNodeWithContentDescription("Add to My List").getUnclippedBoundsInRoot()

        assertEquals(40.96f * TvScale, watch.left.value, OnePixel, "the page's start gutter, where the bar and the bands start")
        assertEquals(12 * TvScale, (list.left - watch.right).value, OnePixel, "`.cover-actions` gap")
    }

    private fun items(count: Int) = (0 until count).map { WallItem("p$it") }

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun text(text: String): DpRect = compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private companion object {
        /** Layout rounds to whole pixels; at the box's density one is half a dp. */
        const val OnePixel = 0.5f
        const val HEAD = "head"
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

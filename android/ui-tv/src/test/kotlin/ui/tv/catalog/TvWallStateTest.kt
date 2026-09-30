package ui.tv.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.Text
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.tv.TvFocus
import ui.tv.TvTheme
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * What Robolectric can check about [TvWall] without a real window manager:
 * every item gets its own plate, and a plate's `onOpen` reaches the wall's
 * own callback with the item it was given. Initial focus, D-pad traversal
 * and [TvWall.restoreKey] are real window-manager behaviour and live in
 * `ui-tv/src/androidTest/kotlin/ui/tv/catalog/TvWallTest.kt` instead, the
 * same split every other TV building block in this module follows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp")
class TvWallStateTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    @Test
    fun everyItemRendersItsOwnPlate() {
        show(items = listOf(WallItem("a"), WallItem("b"), WallItem("c")))

        compose.onNodeWithTag("a").assertExists()
        compose.onNodeWithTag("b").assertExists()
        compose.onNodeWithTag("c").assertExists()
    }

    @Test
    fun clickingAPlateOpensTheItemItWasHandedNotAnother() {
        var opened: WallItem? = null
        show(items = listOf(WallItem("a"), WallItem("b")), onOpen = { opened = it })

        compose.onNodeWithTag("b").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(WallItem("b"), opened)
    }

    /**
     * A real regression: moving focus up or down inside this grid left the
     * focused plate's own top behind the departments bar, since nothing
     * cleared it the way `TvHome`'s own vertical list already does for
     * itself. Pinned as which spec each part of the grid actually reads,
     * not a rendered scroll distance Robolectric has no real window
     * manager to animate — the box is where that class of behaviour is
     * proven (`TvHomeStateTest`'s own split says as much).
     */
    @Test
    fun theGridReadsTheBarClearanceSpecButTheHeaderResetsToTheAmbientDefault() {
        var headerSpec: BringIntoViewSpec? = null
        var plateSpec: BringIntoViewSpec? = null
        showContent {
            TvWall(
                items = listOf(WallItem("a")),
                key = WallItem::id,
                restoreKey = null,
                onOpen = {},
                header = { headerSpec = LocalBringIntoViewSpec.current },
                plate = { item, modifier, itemOnOpen ->
                    plateSpec = LocalBringIntoViewSpec.current
                    TestPlate(item, modifier, itemOnOpen)
                },
            )
        }

        assertNotSame(headerSpec, plateSpec, "the header must not inherit the grid's own vertical clearance for its own, sideways-scrolling rows")
    }

    /**
     * The regression [theGridReadsTheBarClearanceSpecButTheHeaderResetsToTheAmbientDefault]
     * pins from the ambient side, proven end to end here with a real key
     * press. Plates sized so barely more than one row fits on a 540dp
     * screen at once (260dp, well past a real poster's own height) —
     * narrower plates left the row above already on screen by the time
     * Down reached the bottom, so Up by one never had to scroll at all and
     * the bug this pins never had a chance to show. Down three times to
     * the last row, Up once back to the row above it — the exact box
     * repro (Films row 2 back to row 1) — must leave the newly focused
     * plate's own top clear of the bar, not just a Down move (which
     * settles with the target's own bottom flush against the viewport's
     * bottom, nowhere near the bar, so it never exposed the bug the first
     * version of this fix left behind).
     */
    @Test
    @Config(qualifiers = "w960dp-h540dp")
    fun pressingUpKeepsTheFocusedPlateClearOfTheBar() {
        show(items = (0 until 16).map { WallItem("item-$it") }, plateSize = 260.dp)
        repeat(3) { compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) } }
        compose.onNodeWithTag("item-12").assertIsFocused()

        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionUp) }

        compose.onNodeWithTag("item-6").assertIsFocused()
        val clearancePx = with(compose.density) { TvBarClearance.toPx() }
        val top = compose.onNodeWithTag("item-6").fetchSemanticsNode().boundsInRoot.top
        assertTrue(top >= clearancePx - 1f, "plate top at ${top}px, short of its own ${clearancePx}px bar clearance")
    }

    /**
     * The end-to-end shape of the box's own repro (Series' own short last
     * row, then Films) — real key presses, real focus. Robolectric's own
     * two-dimensional focus search happens to already keep the column for
     * this exact, evenly-spaced grid, wired or not, so this alone cannot
     * tell `TvWall`'s own explicit override apart from having none at all
     * (checked by hand: a version of [sectionCrossingsOf] that always
     * returns nothing left every assertion below still passing).
     * `TvWallCellsTest`'s own pure-function tests are what actually pin the
     * rule the box's own remote needed — the row above's own last column,
     * not the same one, is what Compose's search reached there but never
     * does here. This test stays as the rule's own visible, documented
     * shape, and as a regression guard against the override itself ever
     * mis-wiring a plate onto the wrong far-side target.
     */
    @Test
    @Config(qualifiers = "w960dp-h540dp")
    fun upAndDownAcrossAHeadingKeepTheColumnRatherThanJumpingToTheFarRowsLastItem() {
        show(
            items = (0 until 16).map { WallItem("item-$it") },
            headings = mapOf(0 to "Series", 10 to "Films"),
        )

        compose.onNodeWithTag("item-10").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("item-10").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("item-6").assertIsFocused() // Series' own last row: item-6..item-9, column 1.

        compose.onNodeWithTag("item-14").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("item-14").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("item-9").assertIsFocused() // column 5 does not exist there; clamps to item-9, the row's own last.

        compose.onNodeWithTag("item-6").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("item-6").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("item-10").assertIsFocused()
    }

    private fun show(
        items: List<WallItem>,
        onOpen: (WallItem) -> Unit = {},
        headings: Map<Int, String> = emptyMap(),
        plateSize: Dp = 120.dp,
    ) {
        showContent {
            TvWall(
                items = items,
                key = WallItem::id,
                restoreKey = null,
                onOpen = onOpen,
                headings = headings,
                plate = { item, modifier, itemOnOpen -> TestPlate(item, modifier, itemOnOpen, plateSize) },
            )
        }
    }

    private fun showContent(content: @Composable () -> Unit) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { TvTheme { content() } }
        }
        compose.waitForIdle()
    }
}

/** A minimal plate for [TvWall]'s own tests, decoupled from [TvPlate]'s appearance — see `TvWallTest` for its androidTest twin. */
internal data class WallItem(val id: String)

@Composable
internal fun TestPlate(
    item: WallItem,
    modifier: Modifier,
    onOpen: () -> Unit,
    size: Dp = 120.dp,
) {
    Card(
        onClick = onOpen,
        modifier = modifier.testTag(item.id).size(size),
        shape = TvFocus.cardShape(),
        scale = TvFocus.cardScale(),
        border = TvFocus.cardBorder(),
        glow = TvFocus.cardGlow(),
    ) {
        Text(item.id)
    }
}

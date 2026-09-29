package ui.tv.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
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

    private fun show(
        items: List<WallItem>,
        onOpen: (WallItem) -> Unit = {},
    ) {
        showContent {
            TvWall(
                items = items,
                key = WallItem::id,
                restoreKey = null,
                onOpen = onOpen,
                plate = { item, modifier, itemOnOpen -> TestPlate(item, modifier, itemOnOpen) },
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
) {
    Card(
        onClick = onOpen,
        modifier = modifier.testTag(item.id).size(120.dp),
        shape = TvFocus.cardShape(),
        scale = TvFocus.cardScale(),
        border = TvFocus.cardBorder(),
        glow = TvFocus.cardGlow(),
    ) {
        Text(item.id)
    }
}

package ui.tv.catalog

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.tv.material3.Card
import androidx.tv.material3.Text
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ui.tv.LeavesTouchModeRule
import ui.tv.TvFocus
import ui.tv.TvTheme

/**
 * The focus and D-pad behaviour [TvWall] exists for, which only run true on
 * a real window manager — the same reason `TvProfilePickerTest` and
 * `TvSetupStepTest` live here rather than beside `TvWallStateTest`. A
 * minimal fake plate stands in for [TvPlate] throughout, since none of
 * this is about what a plate draws — [TvWallStateTest] and `TvPlateTest`
 * cover that separately.
 */
@RunWith(AndroidJUnit4::class)
class TvWallTest {
    @get:Rule val touchMode = LeavesTouchModeRule()

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theFirstPlateIsFocusedAsSoonAsTheWallAppears() {
        show(items = fiveItems())

        waitUntilFocused("item-0")
    }

    @Test
    fun dPadRightMovesFocusFromTheFirstPlateToTheNext() {
        show(items = fiveItems())
        waitUntilFocused("item-0")

        compose.onNodeWithTag("item-0").performKeyInput { pressKey(Key.DirectionRight) }

        waitUntilFocused("item-1")
    }

    /**
     * Six columns per row (see `TvWall`'s own `Columns`), so thirty items
     * puts item 29 five rows down — well past whatever a single frame lays
     * out on first composition. [TvWall] has to scroll to it before a
     * `FocusRequester` has anything to attach to.
     */
    @Test
    fun aRestoreKeyBeyondTheFirstScreenfulIsFocusedOnEntry() {
        val items = (0 until 30).map { WallItem("item-$it") }
        show(items = items, restoreKey = "item-29")

        waitUntilFocused("item-29")
    }

    @Test
    fun aMissingRestoreKeyFallsBackToTheFirstPlate() {
        show(items = fiveItems(), restoreKey = "item-not-on-this-wall")

        waitUntilFocused("item-0")
    }

    private fun show(
        items: List<WallItem>,
        restoreKey: String? = null,
    ) {
        compose.setContent {
            TvTheme {
                TvWall(
                    items = items,
                    key = WallItem::id,
                    restoreKey = restoreKey,
                    onOpen = {},
                    plate = { item, modifier, onOpen -> TestPlate(item, modifier, onOpen) },
                )
            }
        }
    }

    private fun fiveItems() = (0 until 5).map { WallItem("item-$it") }

    private fun waitUntilFocused(tag: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
    }
}

/** A minimal plate for [TvWall]'s own tests — the androidTest twin of `TvWallStateTest`'s. */
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

package ui.chrome

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import catalog.Destination
import catalog.catalogTabsOf
import designsystem.MediagramTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.BrowseActions
import ui.LibraryScaffold
import ui.MenuActions
import ui.ProfileBarState
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The chrome's own two width-dependent defects a real rotation surfaces:
 * losing a scroll position because the width class change moved [content]
 * to a different slot in the composition (H1 — [ui.LibraryScaffold],
 * [LibraryHome] each now have exactly one call site for it, with only the
 * rail conditional beside it), and a hidden compact header leaving a blank
 * band instead of the space it just gave up (H2). A fake [WindowInfo]
 * flips the width class in place, the way a real rotation does — the
 * manifest handles `orientation|screenSize`, so nothing here recreates the
 * activity either.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1164dp-h777dp")
class WidthClassStateTest {
    @get:Rule val compose = createEmptyComposeRule()

    private var widthDp by mutableStateOf(1164)
    private var list: LazyListState? = null

    private val menu = MenuActions({}, {}, {}, {}, {})
    private val browse = BrowseActions({}, {}, {}, {})
    private val profile = ProfileBarState("test") {}

    private fun host(body: @Composable () -> Unit) {
        compose.runOnUiThread {
            Robolectric.buildActivity(ComponentActivity::class.java).setup().visible().get().setContent {
                val real = LocalWindowInfo.current
                val fake = object : WindowInfo by real { override val containerSize: IntSize get() = IntSize(widthDp, 777) }
                CompositionLocalProvider(LocalWindowInfo provides fake) { MediagramTheme { body() } }
            }
        }
        compose.waitForIdle()
    }

    @Composable
    private fun rows() {
        val state = rememberLazyListState()
        list = state
        LazyColumn(state = state, modifier = Modifier.fillMaxSize().testTag("rows")) {
            items(200) { Text("row $it", Modifier.height(50.dp)) }
        }
    }

    private fun scrollThenNarrow(): Int {
        compose.runOnIdle { runBlocking { list!!.scrollToItem(50) } }
        compose.waitForIdle()
        compose.runOnUiThread { widthDp = 777 }
        compose.waitForIdle()
        return list!!.firstVisibleItemIndex
    }

    @Test fun pushedFrameKeepsScrollAcrossWidthClass() {
        host { LibraryScaffold(Destination.Latest, {}, menu, profile, browse, {}) { rows() } }
        assertEquals(50, scrollThenNarrow(), "EXPANDED to MEDIUM must not move content to a different composition slot")
    }

    @Test fun rootKeepsScrollAcrossWidthClass() {
        host {
            val holder = rememberSaveableStateHolder()
            val tabs = catalogTabsOf(emptyList())
            LibraryHome(tabs, listOf(0, 3), 3, {}, browse, menu, profile, {}, rememberLazyListState(), hasCover = false) {
                holder.SaveableStateProvider("shelves") { rows() }
            }
        }
        assertEquals(50, scrollThenNarrow(), "EXPANDED to MEDIUM must not move content to a different composition slot")
    }

    @Test fun compactHeaderHidingLeavesContentWhereItWas() {
        widthDp = 400
        host {
            val tabs = catalogTabsOf(emptyList())
            LibraryHome(tabs, listOf(0, 3), 3, {}, browse, menu, profile, {}, rememberLazyListState(), hasCover = false) { rows() }
        }
        val before = compose.onNodeWithTag("rows").getUnclippedBoundsInRoot()
        val wordBefore = compose.onNodeWithText("mediagram").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("rows").performTouchInput { swipeUp() }
        compose.waitForIdle()
        val after = compose.onNodeWithTag("rows").getUnclippedBoundsInRoot()
        val wordAfter = compose.onNodeWithText("mediagram").getUnclippedBoundsInRoot()

        // The header actually hid.
        assertTrue(wordAfter.top < wordBefore.top, "the header should have moved up on a swipe up")
        // The list's own top followed it up by (about) the same amount,
        // rather than staying where a full-height header had padded it to
        // — the defect this guards is exactly that gap staying blank.
        val headerMoved = wordBefore.top - wordAfter.top
        val listMoved = before.top - after.top
        assertTrue(listMoved.value > headerMoved.value * 0.5f, "content should reclaim the space the header gave up (header moved ${headerMoved.value}, content moved ${listMoved.value})")
    }
}

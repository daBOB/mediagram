package ui.tv.catalog

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A bar over a page whose hero holds no stop: leaving for the bar brings the hero back. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvToTopWhenLeftForTheBarTest : TvScreenStateTest() {
    private val state = LazyListState()

    private fun showPage() =
        show {
            val bar = remember { FocusRequester() }
            Column {
                Box(Modifier.testTag("bar").focusRequester(bar).focusable().height(40.dp).fillMaxWidth())
                LazyColumn(state = state, modifier = Modifier.toTopWhenLeftForTheBar { state.animateScrollToItem(0) }) {
                    item { Box(Modifier.height(360.dp)) }
                    items(4) { Box(Modifier.testTag("row$it").focusable().height(300.dp).fillMaxWidth()) }
                }
            }
            LaunchedEffect(Unit) { bar.requestFocus() }
        }

    private fun press(key: Key) = compose.onNode(isFocused()).performKeyInput { pressKey(key) }

    @Test
    fun upOffTheFirstRowScrollsTheHeroBack() {
        showPage()
        repeat(3) { press(Key.DirectionDown) }
        compose.waitForIdle()
        assertNotEquals(0, state.firstVisibleItemIndex)

        repeat(3) { press(Key.DirectionUp) }
        compose.waitForIdle()

        compose.onNodeWithTag("bar").assertIsFocused()
        assertEquals(0 to 0, state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset)
    }

    @Test
    fun upInsideThePageLeavesTheScrollToTheFocusedRow() {
        showPage()
        repeat(4) { press(Key.DirectionDown) }
        press(Key.DirectionUp)
        compose.waitForIdle()

        compose.onNodeWithTag("row2").assertIsFocused()
        assertNotEquals(0, state.firstVisibleItemIndex)
    }
}

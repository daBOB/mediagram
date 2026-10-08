package ui.tv.catalog

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Anime with nothing underway: the wall's header is the hero alone, taller
 * than the screen, so no plate is laid out when Down from the bar asks the
 * page to take the remote — and the remote stayed on the bar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvWallEntryStateTest : TvScreenStateTest() {
    @Test
    fun downFromTheBarReachesThePlatesUnderAHeroThatFillsTheScreenAndUpReturns() {
        show {
            val bar = remember { FocusRequester() }
            val page = remember { FocusRequester() }
            Column {
                Box(Modifier.testTag("bar").focusRequester(bar).focusProperties { down = page }.focusable().height(40.dp).fillMaxWidth())
                Box(Modifier.focusRequester(page)) {
                    CompositionLocalProvider(LocalTakesArrivalFocus provides false) {
                        TvWall(
                            items = (0 until 12).map { WallItem("item-$it") },
                            key = WallItem::id,
                            restoreKey = null,
                            onOpen = {},
                            header = { Box(Modifier.height(600.dp).fillMaxWidth()) },
                            headerHoldsNoStop = true,
                            plate = { item, modifier, onOpen -> TestPlate(item, modifier, onOpen) },
                        )
                    }
                }
            }
            LaunchedEffect(Unit) { bar.requestFocus() }
        }
        compose.onNodeWithTag("bar").assertIsFocused()

        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()

        compose.onNodeWithTag("item-0").assertIsFocused()

        // The header passes the entry on, never holds the remote: Up still leaves for the bar.
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionUp) }
        compose.waitForIdle()
        compose.onNodeWithTag("bar").assertIsFocused()
    }
}

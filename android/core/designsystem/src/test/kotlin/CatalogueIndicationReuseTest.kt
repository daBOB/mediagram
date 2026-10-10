package designsystem

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * A lazy list parks a card mid-press, cutting the node's collector off before
 * the Release. The drawn shrink and tint cannot be read back here, so this
 * checks that a node reused with a new source stays laid out at full size and
 * still takes its click, i.e. the reset path runs without breaking the node.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatalogueIndicationReuseTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    @Test
    fun aNodeParkedMidPressComesBackAtRestAndTakesClicks() {
        var active by mutableStateOf(true)
        var source by mutableStateOf(MutableInteractionSource())
        var clicks = 0
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                ReusableContentHost(active) {
                    Box(
                        Modifier
                            .size(100.dp)
                            .testTag("c")
                            .clickable(interactionSource = source, indication = catalogueIndication()) { clicks++ },
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.runOnUiThread { source.tryEmit(PressInteraction.Press(Offset.Zero)) }
        compose.waitForIdle()

        compose.runOnUiThread { active = false }
        compose.waitForIdle()
        compose.runOnUiThread {
            source = MutableInteractionSource()
            active = true
        }
        compose.waitForIdle()

        assertEquals(100f, compose.onNodeWithTag("c").getBoundsInRoot().width.value, 0.3f)
        compose.onNodeWithTag("c").performClick()
        compose.waitForIdle()
        assertEquals(1, clicks)
    }
}

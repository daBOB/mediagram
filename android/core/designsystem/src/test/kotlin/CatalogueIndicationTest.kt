package designsystem

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * The tint, the ring and the 0.98 snap are drawn, and this project's
 * Robolectric setup cannot capture pixels (see PageGroundColorTest in
 * ui-mobile), so those are checked on the device. What is checked here:
 * the factory is stable, so a recomposition never rebuilds every
 * clickable's node, and a clickable drawing it still takes its click.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatalogueIndicationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    @Test
    fun theFactoryIsEqualForEqualPressScales() {
        assertEquals(catalogueIndication(), catalogueIndication())
        assertNotEquals(catalogueIndication(), catalogueIndication(pressScale = 1f))
    }

    @Test
    fun aClickableDrawingItStillTakesItsClickThroughHoverAndPress() {
        var clicks = 0
        val source = MutableInteractionSource()
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                Box(
                    Modifier
                        .size(48.dp)
                        .testTag("c")
                        .clickable(interactionSource = source, indication = catalogueIndication()) { clicks++ },
                )
            }
        }
        compose.waitForIdle()
        compose.runOnUiThread { source.tryEmit(HoverInteraction.Enter()) }
        compose.onNodeWithTag("c").performClick()
        compose.waitForIdle()
        assertEquals(1, clicks)
    }
}

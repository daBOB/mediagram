package ui.chrome

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.height
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.down
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import designsystem.MediagramTheme
import kotlinx.coroutines.launch
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatalogueButtonsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    @Test
    fun eachWrapperStillTakesItsClick() {
        val clicks = mutableListOf<String>()
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MediagramTheme {
                    androidx.compose.foundation.layout.Column {
                        CatalogueTextButton(onClick = { clicks += "text" }) { Text("text") }
                        CatalogueButton(onClick = { clicks += "filled" }) { Text("filled") }
                        CatalogueOutlinedButton(onClick = { clicks += "outlined" }) { Text("outlined") }
                        CatalogueIconButton(onClick = { clicks += "icon" }) { Text("icon") }
                    }
                }
            }
        }
        listOf("text", "filled", "outlined", "icon").forEach { compose.onNodeWithText(it).performClick() }
        compose.waitForIdle()
        assertEquals(listOf("text", "filled", "outlined", "icon"), clicks)
    }

    /**
     * Under MediagramTheme a bare Material button answers a press with
     * nothing: the theme turns ripple off, and Material's buttons never read
     * LocalIndication. A new one slipping in would be a silent control.
     */
    @Test
    fun noScreenUsesABareMaterialButton() {
        val bare = Regex("""^import androidx\.compose\.material3\.(TextButton|Button|OutlinedButton|IconButton)$""", RegexOption.MULTILINE)
        val offenders =
            File("src/main/kotlin").walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.name != "CatalogueButtons.kt" }
                .filter { bare.containsMatchIn(it.readText()) }
                .map { it.path }
                .toList()
        assertTrue(offenders.isEmpty(), "use ui.chrome's Catalogue*Button instead in: $offenders")
    }

    /** What an indication saw: the presses its interaction source carried and the box it drew over. */
    private class Seen {
        var presses = 0
        var drawn = IntSize.Zero
    }

    private class RecordingIndication(val seen: Seen) : IndicationNodeFactory {
        override fun create(interactionSource: InteractionSource): DelegatableNode = Node(interactionSource, seen)
        override fun equals(other: Any?) = other is RecordingIndication && other.seen === seen
        override fun hashCode() = seen.hashCode()

        private class Node(val source: InteractionSource, val seen: Seen) : Modifier.Node(), LayoutAwareModifierNode {
            override fun onAttach() {
                coroutineScope.launch { source.interactions.collect { if (it is PressInteraction.Press) seen.presses++ } }
            }
            // Robolectric draws nothing here; the box an indication draws over is the box it is measured at.
            override fun onRemeasured(size: IntSize) {
                seen.drawn = size
            }
        }
    }

    private val names = listOf("text", "filled", "outlined", "icon")
    private val seen = (names + "pill").associateWith { Seen() }
    private var density = 1f

    private fun show(enabled: Boolean = true, clicks: MutableList<String> = mutableListOf()) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                density = LocalDensity.current.density
                MediagramTheme {
                    Column {
                        CompositionLocalProvider(LocalIndication provides RecordingIndication(seen.getValue("text"))) {
                            CatalogueTextButton(onClick = { clicks += "text" }, enabled = enabled) { Text("text", fontSize = 10.sp, lineHeight = 10.sp) }
                        }
                        CompositionLocalProvider(LocalIndication provides RecordingIndication(seen.getValue("filled"))) {
                            CatalogueButton(onClick = { clicks += "filled" }, enabled = enabled) { Text("filled", fontSize = 10.sp, lineHeight = 10.sp) }
                        }
                        CompositionLocalProvider(LocalIndication provides RecordingIndication(seen.getValue("outlined"))) {
                            CatalogueOutlinedButton(onClick = { clicks += "outlined" }, enabled = enabled) { Text("outlined", fontSize = 10.sp, lineHeight = 10.sp) }
                        }
                        CompositionLocalProvider(LocalIndication provides RecordingIndication(seen.getValue("pill"))) {
                            CatalogueOutlinedButton(
                                onClick = {},
                                modifier = Modifier.height(36.dp).semantics { contentDescription = "pill" },
                            ) { Text("pill", fontSize = 10.sp, lineHeight = 10.sp) }
                        }
                        CompositionLocalProvider(LocalIndication provides RecordingIndication(seen.getValue("icon"))) {
                            CatalogueIconButton(onClick = { clicks += "icon" }, enabled = enabled) { Text("icon", fontSize = 10.sp, lineHeight = 10.sp) }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aPressOnEachWrapperReachesTheThemesIndication() {
        show()
        names.forEach { name ->
            compose.onNodeWithText(name).performTouchInput { down(center) }
            compose.waitForIdle()
            assertEquals(1, seen.getValue(name).presses, "$name press reached the indication")
            compose.onNodeWithText(name).performTouchInput { up() }
        }
    }

    @Test
    fun theIndicationLandsOnTheDrawnContainerNotOnTheTouchBox() {
        show()
        val d = density
        // Material's icon button draws a 40dp circle inside its 48dp touch box.
        compose.onNodeWithText("icon").assertHeightIsEqualTo(40.dp).assertWidthIsEqualTo(40.dp)
            .assertTouchHeightIsEqualTo(48.dp).assertTouchWidthIsEqualTo(48.dp)
        assertEquals(IntSize((40 * d).toInt(), (40 * d).toInt()), seen.getValue("icon").drawn, "indication is the 40dp circle")
        // A caller's own 36dp height: indication covers the pill, touch box and caller semantics survive.
        compose.onNodeWithContentDescription("pill").assertHasClickAction().assertHeightIsEqualTo(36.dp)
            .assertTouchHeightIsEqualTo(48.dp)
        assertEquals((36 * d).toInt(), seen.getValue("pill").drawn.height, "indication covers the 36dp pill")
    }

    @Test
    fun aDisabledWrapperTakesNoClick() {
        val clicks = mutableListOf<String>()
        show(enabled = false, clicks = clicks)
        names.forEach { compose.onNodeWithText(it).performClick() }
        compose.waitForIdle()
        assertTrue(clicks.isEmpty(), "disabled wrappers clicked: $clicks")
    }
}

package ui.player

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/** The seek bar in a phone's card at an accessibility font size: the times must not squeeze it to a sliver. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerScrubberTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    @Test
    fun theBarKeepsAUsableWidthAtALargeFontSize() {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.5f)) {
                    MaterialTheme {
                        // The card's inside on a 360dp phone: 12dp margin and 16dp padding each side.
                        Box(modifier = Modifier.padding(28.dp)) {
                            PlayerScrubber(
                                positionMs = 3_724_000L,
                                durationMs = 7_110_000L,
                                endsLabel = "ends 23:41",
                                scrubbingTo = null,
                                onScrubbingToChange = {},
                                onSeek = {},
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()

        val bar = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).getBoundsInRoot()
        assertTrue((bar.right - bar.left) >= 160.dp, "the bar is ${bar.right - bar.left} wide")
    }
}

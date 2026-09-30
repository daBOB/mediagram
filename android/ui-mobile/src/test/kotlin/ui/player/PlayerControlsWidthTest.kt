package ui.player

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.media3.common.Player
import io.mockk.mockk
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

/**
 * The transport row at real phone widths, measured with real text: every
 * button, the settings gear and CC and Next among them, must keep a width.
 * `FlowRow` wraps the row instead of squeezing the last buttons to nothing.
 */
abstract class PlayerControlsWidthCases {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(hasNext: Boolean) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MaterialTheme {
                    PlayerControls(
                        player = mockk<Player>(relaxed = true),
                        onScrubbingChanged = {},
                        statsShown = false,
                        onToggleStats = {},
                        speed = 1.5f,
                        onOpenSettings = {},
                        hasSubtitles = true,
                        subtitlesOn = false,
                        onToggleSubtitles = {},
                        catalogedDurationSecs = 600,
                        hasNext = hasNext,
                        nextTitleLine = "Next",
                        onPlayNext = {},
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertEveryButtonHasWidth(hasNext: Boolean) {
        show(hasNext)
        val names = listOf("Show playback statistics", "Subtitles off", "Playback settings") + if (hasNext) listOf("Play next: Next") else emptyList()
        for (name in names) {
            val bounds = compose.onNodeWithContentDescription(name).getBoundsInRoot()
            assertTrue((bounds.right - bounds.left).value > 0f, "$name must keep a width")
        }
    }

    @Test
    fun aFilmKeepsEveryButton() = assertEveryButtonHasWidth(hasNext = false)

    @Test
    fun anEpisodeWithNextKeepsEveryButton() = assertEveryButtonHasWidth(hasNext = true)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerControlsWidth360Test : PlayerControlsWidthCases()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerControlsWidth411Test : PlayerControlsWidthCases()

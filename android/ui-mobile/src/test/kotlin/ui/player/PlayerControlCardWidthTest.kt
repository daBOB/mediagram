package ui.player

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import io.mockk.every
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
import playback.AudioOption
import player.PlayerChoices
import player.SUBTITLES_OFF
import player.SubtitleOption
import player.UpNextUiState
import kotlin.test.assertTrue

/**
 * The card at real phone widths, measured with real text, with every control
 * it can carry: each keeps a full 48dp touch target, because a row wraps
 * rather than squeezing its last buttons. On a tablet the card stops at 720dp.
 */
abstract class PlayerControlCardWidthCases {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    protected fun show() {
        val player =
            mockk<Player>(relaxed = true) {
                every { seekBackIncrement } returns 15_000L
                every { seekForwardIncrement } returns 15_000L
            }
        val choices =
            PlayerChoices(
                speed = 1.5f,
                audioOptions = listOf(AudioOption(0, 0, "en", "English", true), AudioOption(1, 0, "de", "German", false)),
                subtitleOptions = listOf(SubtitleOption(SUBTITLES_OFF, "Off", true), SubtitleOption("en", "English", false)),
                subtitleStyleVisible = true,
            )
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MaterialTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        PlayerControlCard(
                            player = player,
                            view =
                                PlayerCardView(
                                    choices = choices,
                                    upNext = UpNextUiState(run = listOf("a", "b", "c"), hasPrevious = true, hasNext = true),
                                    statsShown = false,
                                    hasEpisodes = true,
                                    catalogedDurationSecs = 600,
                                ),
                            actions =
                                PlayerCardActions(
                                    onRestart = {}, onPrevious = {}, onNext = {}, onToggleSubtitles = {},
                                    onOpenMenu = {}, onToggleStats = {}, onEpisodes = {}, onEnterPip = {},
                                ),
                            onScrubbingChanged = {},
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private val names =
        listOf(
            "Restart", "Previous", "Back 15 seconds", "Play", "Forward 15 seconds", "Next", "Stats", "Episodes",
            "Subtitles", "Subtitle options", "Speed", "Audio", "Framing", "Picture in picture",
        )

    @Test
    fun everyControlKeepsAFullTouchTarget() {
        show()
        for (name in names) {
            compose.onNodeWithContentDescription(name).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerControlCardWidth360Test : PlayerControlCardWidthCases()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerControlCardWidth411Test : PlayerControlCardWidthCases()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerControlCardTabletTest : PlayerControlCardWidthCases() {
    @Test
    fun onATabletTheCardStopsAt720dp() {
        show()
        val card = compose.onNodeWithTag(PlayerCardTag).getBoundsInRoot()
        assertTrue((card.right - card.left).value <= 720.5f, "the card is ${card.right - card.left} wide")
    }
}

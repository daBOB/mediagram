package ui.player

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.After
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import playback.Framing
import player.SubtitleOption
import kotlin.test.Test

/**
 * Which subtitle sections the sheet draws: language rows follow a regular
 * track, size and sync follow any track that can show, and a title with
 * neither gets neither.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerSettingsSheetGatesTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun open(
        options: List<SubtitleOption>,
        styleVisible: Boolean,
    ) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MaterialTheme {
                    PlayerSettingsSheet(
                        currentSpeed = 1f,
                        onSpeedChosen = {},
                        audioOptions = emptyList(),
                        onAudioChosen = {},
                        subtitleOptions = options,
                        subtitleStyleVisible = styleVisible,
                        onSubtitleChosen = {},
                        subtitleSizePercent = 100,
                        onSubtitleSizeChosen = {},
                        subtitleBacking = "shadow",
                        onSubtitleBackingChosen = {},
                        subtitleOffsetMs = 0L,
                        onSubtitleNudge = {},
                        onSubtitleOffsetReset = {},
                        framing = Framing.Default,
                        onFramingChosen = {},
                        onDismiss = {},
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aForcedOnlyTitleGetsTheStyleSectionAndNoLanguageRows() {
        open(options = emptyList(), styleVisible = true)

        compose.onNodeWithText("Subtitle style").assertExists()
        compose.onNodeWithText("Subtitles").assertDoesNotExist()
    }

    @Test
    fun aTitleWithARegularTrackGetsBoth() {
        open(options = listOf(SubtitleOption("off", "Off", true), SubtitleOption("0", "English", false)), styleVisible = true)

        compose.onNodeWithText("Subtitles").assertExists()
        compose.onNodeWithText("Subtitle style").assertExists()
    }

    @Test
    fun aTitleWithNoTracksGetsNeither() {
        open(options = emptyList(), styleVisible = false)

        compose.onNodeWithText("Subtitles").assertDoesNotExist()
        compose.onNodeWithText("Subtitle style").assertDoesNotExist()
    }
}

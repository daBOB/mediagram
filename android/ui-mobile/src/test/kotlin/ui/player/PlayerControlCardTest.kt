package ui.player

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.common.PlaybackParameters
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
import playback.Framing
import player.PlayerChoices
import player.SUBTITLES_OFF
import player.SubtitleOption
import player.UpNextUiState
import java.time.Duration
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the card offers for what is open: ⏮ ⏭ and ☰ follow the run, CC is
 * disabled rather than hidden without a track, the tools name the current
 * choice, the skips read the player's own fifteen seconds, and each button
 * asks for its own thing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp")
class PlayerControlCardTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    private val pressed = mutableListOf<String>()

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private val subtitled =
        PlayerChoices(
            subtitleOptions = listOf(SubtitleOption(SUBTITLES_OFF, "Off", true), SubtitleOption("en", "English", false)),
            subtitleStyleVisible = true,
        )

    private fun show(
        choices: PlayerChoices = PlayerChoices(),
        upNext: UpNextUiState = UpNextUiState(),
        hasEpisodes: Boolean = false,
        playing: Float = choices.speed,
    ) {
        val player =
            mockk<Player>(relaxed = true) {
                every { seekBackIncrement } returns 15_000L
                every { seekForwardIncrement } returns 15_000L
                every { playbackParameters } returns PlaybackParameters(playing)
            }
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MaterialTheme {
                    PlayerControlCard(
                        player = player,
                        view = PlayerCardView(choices, upNext, statsShown = false, hasEpisodes = hasEpisodes, catalogedDurationSecs = 600),
                        actions =
                            PlayerCardActions(
                                onRestart = { pressed += "restart" },
                                onPrevious = { pressed += "previous" },
                                onNext = { pressed += "next" },
                                onToggleSubtitles = { pressed += "cc" },
                                onOpenMenu = { pressed += "menu:$it" },
                                onToggleStats = { pressed += "stats" },
                                onEpisodes = { pressed += "episodes" },
                                onEnterPip = null,
                            ),
                        onScrubbingChanged = {},
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aFilmHasNeitherStepsNorEpisodes() {
        show()

        compose.onNodeWithContentDescription("Restart").assertExists()
        compose.onNodeWithContentDescription("Previous").assertDoesNotExist()
        compose.onNodeWithContentDescription("Next").assertDoesNotExist()
        compose.onNodeWithContentDescription("Episodes").assertDoesNotExist()
    }

    @Test
    fun theFirstTitleOfARunHasPreviousDisabledAndNextEnabled() {
        show(upNext = UpNextUiState(run = listOf("a", "b"), hasNext = true), hasEpisodes = true)

        compose.onNodeWithContentDescription("Previous").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Next").assertIsEnabled()
        compose.onNodeWithContentDescription("Episodes").assertIsEnabled()
    }

    @Test
    fun withNoSubtitleTrackCcIsDisabledNotHidden() {
        show()

        compose.onNodeWithContentDescription("Subtitles").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Subtitle options").assertIsNotEnabled()
    }

    @Test
    fun ccAndStatsSayWhetherTheyAreOn() {
        show(PlayerChoices(subtitleOptions = listOf(SubtitleOption(SUBTITLES_OFF, "Off", false), SubtitleOption("en", "English", true))))

        compose.onNodeWithContentDescription("Subtitles").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "On"))
        compose.onNodeWithContentDescription("Stats").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Off"))
    }

    @Test
    fun aSingleAudioTrackOffersNoAudioMenu() {
        show(subtitled)

        compose.onNodeWithContentDescription("Audio").assertDoesNotExist()
    }

    @Test
    fun speedAndFramingSayWhatIsChosen() {
        show(PlayerChoices(speed = 1.5f, framing = Framing.FILL))

        compose.onNodeWithText("1.5×").assertExists()
        compose.onNodeWithText("Fill").assertExists()
    }

    /** As on the web: another screen in a watch-together session changed it, and this one plays at its speed. */
    @Test
    fun speedSaysTheSpeedPlayingWhereThatIsNotTheOneChosen() {
        show(PlayerChoices(speed = 1f), playing = 1.5f)

        compose.onNodeWithText("1.5×").assertExists()
        compose.onNodeWithText("1×").assertDoesNotExist()
    }

    /** Ten minutes left, played at 1.5×, end in six minutes forty: the "ends" clock counts the speed playing. */
    @Test
    fun theEndsLineCountsTheSpeedPlaying() {
        val before = LocalTime.now()
        show(PlayerChoices(speed = 1f), playing = 1.5f)

        val ends = compose.onNodeWithText("ends ", substring = true).fetchSemanticsNode().config[SemanticsProperties.Text].first().text
        val at = LocalTime.parse(ends.substringAfter("ends "))
        val ahead = Math.floorMod(Duration.between(before.withSecond(0).withNano(0), at).toMinutes(), 24 * 60)
        assertTrue(ahead in 6L..7L, "ends $ahead minutes from now")
    }

    @Test
    fun theSkipsSayThePlayersOwnFifteenSeconds() {
        show()

        compose.onNodeWithText("−15").assertExists()
        compose.onNodeWithText("+15").assertExists()
        compose.onNodeWithContentDescription("Back 15 seconds").assertExists()
        compose.onNodeWithContentDescription("Forward 15 seconds").assertExists()
    }

    @Test
    fun eachButtonAsksForItsOwnThing() {
        show(subtitled, UpNextUiState(run = listOf("a", "b", "c"), hasPrevious = true, hasNext = true), hasEpisodes = true)

        for (name in listOf("Restart", "Previous", "Next", "Subtitles", "Subtitle options", "Speed", "Framing", "Stats", "Episodes")) {
            compose.onNodeWithContentDescription(name).performClick()
        }

        assertEquals(
            listOf("restart", "previous", "next", "cc", "menu:Subtitles", "menu:Speed", "menu:Framing", "stats", "episodes"),
            pressed,
        )
    }
}

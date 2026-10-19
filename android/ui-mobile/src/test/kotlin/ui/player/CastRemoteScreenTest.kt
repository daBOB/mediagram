package ui.player

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import io.mockk.every
import io.mockk.mockk
import model.together.CastTracks
import model.together.CastWords
import model.together.Track
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import player.together.RemoteUi
import kotlin.test.assertEquals

/**
 * The remote on a phone or tablet, as the web's: the player's own top bar
 * and control card over the title, "Playing on TV", Stop where
 * picture-in-picture sits, the player's 15 s skips, the TV's own subtitles
 * and audio, and nothing only this screen's own picture has. It goes when
 * the cast does, after the words it ended with.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w800dp-h600dp")
class CastRemoteScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    private val pressed = mutableListOf<String>()
    private var ui: RemoteUi? by mutableStateOf(null)

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private val english = Track("a1", "English")

    private fun remote(
        audio: List<Track> = listOf(english),
        over: String? = null,
    ) = RemoteUi(
        id = 7, title = "Dune", backdrop = null, label = CastWords.PLAYING_ON_TV, notice = null,
        tracks = CastTracks(audio = audio, text = listOf(Track("t1", "German"))),
        audio = "a1", text = "", hasNext = true, over = over,
    )

    private fun show(first: RemoteUi?) {
        ui = first
        val player =
            mockk<Player>(relaxed = true) {
                every { seekBackIncrement } returns 15_000L
                every { seekForwardIncrement } returns 15_000L
                every { playbackParameters } returns PlaybackParameters(1f)
            }
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MaterialTheme {
                    CastRemote(
                        ui = ui,
                        player = player,
                        onNext = { pressed += "next" },
                        onAudio = { pressed += "audio:$it" },
                        onText = { pressed += "text:$it" },
                        onStop = { pressed += "stop" },
                        onBack = { pressed += "back" },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theRemoteShowsPlayingOnTvAndStop() {
        show(remote())

        compose.onNodeWithText("Dune").assertExists()
        compose.onNode(hasContentDescription(CastWords.PLAYING_ON_TV)).assertExists()
        compose.onNodeWithText(CastWords.STOP).performClick()
        assertEquals(listOf("stop"), pressed)
    }

    @Test
    fun theRemoteHasNoPictureInPicture() {
        show(remote())

        compose.onNodeWithContentDescription("Picture in picture").assertDoesNotExist()
    }

    @Test
    fun theRemoteLeavesOutWhatOnlyThisScreensOwnPictureHas() {
        show(remote())

        for (gone in listOf("Restart", "Previous", "Stats", "Framing", "Episodes")) {
            compose.onNodeWithContentDescription(gone).assertDoesNotExist()
        }
        compose.onNodeWithText("Watch together").assertDoesNotExist()
        compose.onNodeWithText(CastWords.PLAY_ON_TV).assertDoesNotExist()
    }

    @Test
    fun theRemoteSkipsByThePlayersFifteenSeconds() {
        show(remote())

        compose.onNodeWithText("−15").assertExists()
        compose.onNodeWithText("+15").assertExists()
        compose.onNodeWithContentDescription("Next").performClick()
        assertEquals(listOf("next"), pressed)
    }

    @Test
    fun theAudioMenuIsHiddenWithOneTrack() {
        show(remote(audio = listOf(english)))
        compose.onNodeWithContentDescription("Audio").assertDoesNotExist()

        compose.runOnUiThread { ui = remote(audio = listOf(english, Track("a2", "Japanese"))) }
        compose.onNodeWithContentDescription("Audio").performClick()
        compose.onNodeWithText("Japanese").performClick()
        assertEquals(listOf("audio:a2"), pressed)
    }

    @Test
    fun theSubtitlesMenuListsOffAndTheTvsTracks() {
        show(remote())

        compose.onNodeWithContentDescription("Subtitle options").performClick()
        compose.onNodeWithText("Off").assertExists()
        compose.onNodeWithText("German").performClick()
        assertEquals(listOf("text:t1"), pressed)
    }

    @Test
    fun theRemoteWaitsForItsFirstDrawAndGoesWhenTheCastIsGone() {
        show(null)
        compose.runOnUiThread { ui = remote() }
        compose.waitForIdle()
        assertEquals(emptyList(), pressed)

        compose.runOnUiThread { ui = null }
        compose.waitForIdle()
        assertEquals(listOf("back"), pressed)
    }

    @Test
    fun theRemotePopsAfterTheWordsItEndedWith() {
        show(remote())
        compose.mainClock.autoAdvance = false

        compose.runOnUiThread { ui = remote(over = CastWords.STOPPED) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText(CastWords.STOPPED).assertExists()
        assertEquals(emptyList(), pressed)

        compose.mainClock.advanceTimeBy(1_100)
        assertEquals(listOf("back"), pressed)
    }
}

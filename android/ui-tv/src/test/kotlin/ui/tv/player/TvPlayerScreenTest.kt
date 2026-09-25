package ui.tv.player

import android.content.res.Configuration
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import io.mockk.coVerify
import io.mockk.verify
import model.Kind
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import player.CONTROLS_LINGER_MS
import player.PlayerUiState
import ui.tv.catalog.set
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [TvPlayerScreen] driven the way a remote drives it, over a real handle
 * and ViewModel and an ExoPlayer that decodes nothing: which keys bring the
 * controls up and where they land the remote, what Back does with the
 * controls up and with them away, and the phone's lifecycle — Home saves,
 * leaving stops, a configuration change does neither.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvPlayerScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var fixture: TvPlayerFixture
    private lateinit var controller: ActivityController<TvPlayerTestActivity>

    @Before
    fun openPlayer() {
        compose.runOnUiThread {
            fixture = TvPlayerFixture()
            TvPlayerTestActivity.fixture = fixture
            TvPlayerTestActivity.set =
                set("set-one", Kind.EPISODE, "Pilot", show = "A Show", addedAt = 1, episode = 4, durationSecs = 600)
                    .copy(season = 1)
            controller = Robolectric.buildActivity(TvPlayerTestActivity::class.java).setup().visible()
        }
        compose.waitForIdle()
        assertEquals(PlayerUiState.Playing, controller.get().playerViewModel.state.value)
    }

    @After
    fun closePlayer() {
        compose.runOnUiThread {
            if (::controller.isInitialized) controller.close()
            if (::fixture.isInitialized) fixture.close()
        }
    }

    @Test
    fun theControlsAreUpOnOpeningWithTheRemoteOnPlayPause() {
        compose.onNodeWithText("A Show · S1E4 · Pilot").assertExists()
        compose.onNodeWithContentDescription("Pause").assertIsFocused()
        compose.onNodeWithTag(TvSeekBarTag).assertExists()
    }

    @Test
    fun backPutsTheControlsAwayFirstAndOnlyThenLeaves() {
        back()
        compose.onNodeWithTag(TvSeekBarTag).assertDoesNotExist()
        compose.onNodeWithTag(TvPlayerScreenTag).assertIsFocused()
        verify(exactly = 0) { fixture.media.stop() }

        back()
        compose.onNodeWithText("Library").assertExists()
        verify(exactly = 1) { fixture.media.stop() }

        // Left once, stopped once: the Activity stopping afterwards is not a second leaving.
        compose.runOnUiThread { controller.pause().stop() }
        compose.waitForIdle()
        verify(exactly = 1) { fixture.media.stop() }
    }

    @Test
    fun centreWithTheControlsAwayPausesAndBringsThemBackOnPlayPause() {
        back()

        press(Key.DirectionCenter)

        assertFalse(fixture.isPlaying)
        assertEquals(PlayerUiState.Paused, controller.get().playerViewModel.state.value)
        compose.onNodeWithContentDescription("Play").assertIsFocused()
    }

    @Test
    fun centreWithTheControlsUpPressesTheFocusedControlOnce() {
        press(Key.DirectionCenter)

        assertFalse(fixture.isPlaying)
        compose.onNodeWithContentDescription("Play").assertIsFocused()
        press(Key.DirectionCenter)
        assertTrue(fixture.isPlaying)
    }

    @Test
    fun upWithTheControlsAwayLandsOnTheSeekBarWhereLeftSkipsBack() {
        back()

        press(Key.DirectionUp)
        compose.onNodeWithTag(TvSeekBarTag).assertIsFocused()

        press(Key.DirectionLeft)
        assertEquals(32_000L, fixture.positionMs)
        compose.onNodeWithTag(TvSeekBarTag).assertIsFocused()
    }

    @Test
    fun rightWithTheControlsAwaySkipsForwardAndShowsTheSeekBar() {
        back()

        press(Key.DirectionRight)

        assertEquals(52_000L, fixture.positionMs)
        compose.onNodeWithTag(TvSeekBarTag).assertIsFocused()
    }

    @Test
    fun leftAmongTheButtonsMovesFocusRatherThanTheFilm() {
        press(Key.DirectionLeft)

        assertEquals(42_000L, fixture.positionMs)
        compose.onNodeWithContentDescription("Skip back 10 seconds").assertIsFocused()
    }

    @Test
    fun theMediaPlayKeyPausesWithoutMovingTheRemote() {
        press(Key.MediaPlayPause)

        assertFalse(fixture.isPlaying)
        compose.onNodeWithContentDescription("Play").assertIsFocused()
    }

    @Test
    fun theControlsFadeWhilePlayingAndStayWhilePaused() {
        compose.mainClock.advanceTimeBy(CONTROLS_LINGER_MS + 500)
        compose.waitForIdle()
        compose.onNodeWithTag(TvSeekBarTag).assertDoesNotExist()
        compose.onNodeWithTag(TvPlayerScreenTag).assertIsFocused()

        press(Key.DirectionCenter)
        compose.mainClock.advanceTimeBy(CONTROLS_LINGER_MS + 500)
        compose.waitForIdle()
        compose.onNodeWithTag(TvSeekBarTag).assertExists()
    }

    @Test
    fun aConfigurationChangeKeepsPlaying() {
        val oldActivity = controller.get()
        compose.runOnUiThread {
            val turned = Configuration(oldActivity.resources.configuration).apply { orientation = Configuration.ORIENTATION_PORTRAIT }
            controller.configurationChange(turned).visible()
        }
        compose.waitForIdle()
        verify(exactly = 0) { fixture.media.stop() }
    }

    @Test
    fun goingHomeSavesThePositionWithoutStopping() {
        compose.runOnUiThread { controller.pause().stop() }
        compose.waitForIdle()
        coVerify { fixture.repository.setProgress("set-one", 42.0, 600.0) }
        verify(exactly = 0) { fixture.media.stop() }
        compose.runOnUiThread { controller.restart().start().resume() }
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { controller.get().onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
}

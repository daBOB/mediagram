package ui.tv.player

import android.content.res.Configuration
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import player.CONTROLS_LINGER_MS
import player.PlayerUiState
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
class TvPlayerScreenTest : TvPlayerScreenHarness() {
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
        assertEquals(27_000L, fixture.positionMs)
        compose.onNodeWithTag(TvSeekBarTag).assertIsFocused()
    }

    @Test
    fun rightWithTheControlsAwaySkipsForwardAndShowsTheSeekBar() {
        back()

        press(Key.DirectionRight)

        assertEquals(57_000L, fixture.positionMs)
        compose.onNodeWithTag(TvSeekBarTag).assertIsFocused()
    }

    @Test
    fun leftAmongTheButtonsMovesFocusRatherThanTheFilm() {
        press(Key.DirectionLeft)

        assertEquals(42_000L, fixture.positionMs)
        compose.onNodeWithContentDescription("Back 15 seconds").assertIsFocused()
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
        val saved = fixture.repository.snapshot.value.progress.single()
        assertEquals(Triple("set-one", 42.0, 600.0), Triple(saved.setId, saved.at, saved.duration))
        verify(exactly = 0) { fixture.media.stop() }
        compose.runOnUiThread { controller.restart().start().resume() }
        compose.waitForIdle()
    }

    /** -15 at 0:05 lands on 0:00, never before it. */
    @Test
    fun aSkipBackNearTheStartStopsAtTheStart() {
        compose.runOnUiThread { fixture.positionMs = 5_000L }
        back()

        press(Key.DirectionLeft)

        assertEquals(0L, fixture.positionMs)
    }

    /** +15 inside the last fifteen seconds lands on the end, where media3's own ended event takes over. */
    @Test
    fun aSkipForwardNearTheEndStopsAtTheEnd() {
        compose.runOnUiThread { fixture.positionMs = 590_000L }
        back()

        press(Key.DirectionRight)

        assertEquals(fixture.durationMs, fixture.positionMs)
    }

    @Test
    fun theCardIsCentredAtMostItsWidthAndStandsOffTheBottom() {
        val card = compose.onNodeWithTag(TvBottomBandTag).getBoundsInRoot()
        assertEquals(TvCardWidth, card.width)
        assertEquals(100.dp, card.left)
        assertEquals(508.dp, card.bottom)
    }

    @Test
    fun upFromTheTransportReachesTheToolsThenTheSeekBarAndDownComesBack() {
        press(Key.DirectionUp)
        compose.onNodeWithContentDescription("Subtitles").assertIsFocused()
        press(Key.DirectionUp)
        compose.onNodeWithTag(TvSeekBarTag).assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithContentDescription("Subtitles").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithContentDescription("Pause").assertIsFocused()
    }

    @Test
    fun restartGoesBackToTheTopAndKeepsPlaying() {
        toTransport(hasContentDescription("Restart"), Key.DirectionLeft)
        press(Key.DirectionCenter)

        assertEquals(0L, fixture.positionMs)
        assertTrue(fixture.isPlaying)
    }

    /** A title opened on its own has no run: no previous, no next, no episodes — hidden, not disabled. */
    @Test
    fun aTitleWithNoRunHasNoPreviousNextOrEpisodes() {
        compose.onNodeWithContentDescription("Previous").assertDoesNotExist()
        compose.onNodeWithContentDescription("Next").assertDoesNotExist()
        compose.onNodeWithContentDescription("Episodes").assertDoesNotExist()
        compose.onNodeWithContentDescription("Restart").assertExists()
        compose.onNodeWithContentDescription("Stats").assertExists()
    }

    /** Row one reads as on the phone and the web: position, the bar, then the length and when it ends — all on one line. */
    @Test
    fun rowOneIsPositionBarAndDurationThenEndsOnOneLine() {
        val position = compose.onAllNodes(SemanticsMatcher("a clock reading") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { Regex("\\d+:\\d{2}").matches(it.text) } == true
        }).onFirst().getBoundsInRoot()
        val bar = compose.onNodeWithTag(TvSeekBarTag).getBoundsInRoot()
        val length = compose.onNode(SemanticsMatcher("a length then an end time") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { Regex("\\d+:\\d{2} · ends \\d{2}:\\d{2}").matches(it.text) } == true
        }).getBoundsInRoot()

        assertTrue(position.right <= bar.left, "the position ends at ${position.right}, the bar starts at ${bar.left}")
        assertTrue(bar.right <= length.left, "the bar ends at ${bar.right}, the length starts at ${length.left}")
        val middle = (bar.top + bar.bottom) / 2
        assertTrue(position.top < middle && middle < position.bottom, "the position spans ${position.top}..${position.bottom}, the bar's middle is $middle")
        assertTrue(length.top < middle && middle < length.bottom, "the length spans ${length.top}..${length.bottom}, the bar's middle is $middle")
    }
}

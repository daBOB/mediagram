package player.together

import androidx.media3.common.Player
import androidx.media3.common.util.Util
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import model.together.PlaybackChange
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The viewer's controls reach the player through [ViewerPlayer], each move
 * reported once: the media UI's play/pause (`Util.handlePlayPauseButtonAction`,
 * as the card, the TV remote, a headset and the picture-in-picture window
 * press it), seeks and skips.
 */
class ViewerPlayerTest {
    private val reported = mutableListOf<PlaybackChange>()
    private val player = mockk<Player>(relaxed = true) { every { isCommandAvailable(any()) } returns true }
    private val viewer = ViewerPlayer(player) { reported += it }

    @Test
    fun playPauseOnAPausedPlayerIsOnePlay() {
        every { player.playWhenReady } returns false
        every { player.playbackState } returns Player.STATE_READY

        Util.handlePlayPauseButtonAction(viewer)

        verify(exactly = 1) { player.play() }
        assertEquals(listOf(PlaybackChange(playing = true)), reported)
    }

    @Test
    fun playPauseOnAPlayingPlayerIsOnePause() {
        every { player.playWhenReady } returns true
        every { player.playbackState } returns Player.STATE_READY

        Util.handlePlayPauseButtonAction(viewer)

        verify(exactly = 1) { player.pause() }
        assertEquals(listOf(PlaybackChange(playing = false)), reported)
    }

    @Test
    fun aSeekIsReportedWhereItWent() {
        viewer.seekTo(61_500L)
        viewer.seekTo(0, 5_000L)

        verify { player.seekTo(61_500L) }
        assertEquals(listOf(PlaybackChange(pos = 61.5), PlaybackChange(pos = 5.0)), reported)
    }

    @Test
    fun aSkipIsReportedWhereItLanded() {
        every { player.currentPosition } returns 30_000L

        viewer.seekBack()
        viewer.seekForward()

        verify { player.seekBack() }
        verify { player.seekForward() }
        assertEquals(listOf(PlaybackChange(pos = 30.0), PlaybackChange(pos = 30.0)), reported)
    }

    /** The media session pausing an idle player as the app's task goes, say: no viewer's move. */
    @Test
    fun whatTheAppDoesItselfThroughThePlayerIsNotReported() {
        viewer.unreported { viewer.playWhenReady = false }
        viewer.pause()

        verify { player.playWhenReady = false }
        assertEquals(listOf(PlaybackChange(playing = false)), reported)
    }

    @Test
    fun askingToPlayWhenReadyIsReportedAsPlayOrPause() {
        viewer.playWhenReady = false

        assertEquals(listOf(PlaybackChange(playing = false)), reported)
    }
}

package ui.tv.player

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.dp
import data.CatalogRepository
import designsystem.Overscan
import io.mockk.coEvery
import io.mockk.mockk
import model.Kind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import playback.SubtitleTrackSource
import playback.TimedCue
import player.nudgeSubtitleOffset
import player.setSubtitleSize
import ui.tv.catalog.set
import kotlin.test.assertTrue

/**
 * [TvPlayerScreen] playing a title with English subtitles, which the player
 * picks by the shared default rule: the cue at the playhead is drawn, clear
 * of the controls while they are up and inside the overscan margin always,
 * and the viewer's shared size and sync offset apply to it as on the phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvPlayerSubtitlesTest : TvPlayerScreenHarness() {
    override fun makeFixture(): TvPlayerFixture {
        val subtitled =
            set("set-one", Kind.EPISODE, "Pilot", show = "A Show", addedAt = 1, episode = 4, durationSecs = 600)
                .copy(subtitleLanguages = listOf("en"))
        val catalog = mockk<CatalogRepository>(relaxed = true)
        coEvery { catalog.mediaSet("set-one") } returns subtitled
        val subtitles = mockk<SubtitleTrackSource>()
        coEvery { subtitles.load("set-one", "en") } returns
            listOf(
                // Spans the fixture's playhead at 42s.
                TimedCue(40_000, 50_000, SPOKEN),
                // Just after it — on screen only once the offset pulls it earlier.
                TimedCue(42_500, 50_000, LATE),
            )
        return TvPlayerFixture(catalog = catalog, subtitles = subtitles)
    }

    @Test
    fun theCueAtThePlayheadIsDrawnAndTheOneAfterItIsNot() {
        awaitCue(SPOKEN)
        compose.onNodeWithText(LATE).assertDoesNotExist()
    }

    @Test
    fun withTheControlsUpTheCueSitsClearAboveThem() {
        val cue = awaitCue(SPOKEN)
        val seekBar = compose.onNodeWithTag(TvSeekBarTag).getBoundsInRoot()
        assertTrue(cue.bottom < seekBar.top, "cue ends at ${cue.bottom}, the controls start above ${seekBar.top}")
        assertInsideOverscan(cue, compose.onNodeWithTag(TvPlayerScreenTag).getBoundsInRoot())
    }

    @Test
    fun withTheControlsAwayTheCueDropsToTheOverscanMargin() {
        val lifted = awaitCue(SPOKEN)
        back()
        compose.onNodeWithTag(TvSeekBarTag).assertDoesNotExist()
        compose.waitForIdle()

        val dropped = compose.onNodeWithText(SPOKEN).getBoundsInRoot()
        val screen = compose.onNodeWithTag(TvPlayerScreenTag).getBoundsInRoot()
        assertTrue(dropped.bottom > lifted.bottom, "nothing covers the picture now, so the cue comes down")
        assertInsideOverscan(dropped, screen)
    }

    @Test
    fun theSharedSizeChoiceScalesTheCue() {
        val normal = awaitCue(SPOKEN)
        compose.runOnUiThread { controller.get().playerViewModel.setSubtitleSize(200) }
        compose.waitForIdle()

        val doubled = compose.onNodeWithText(SPOKEN).getBoundsInRoot()
        assertTrue(doubled.height > normal.height * 1.5f, "200% draws taller than 100% (${normal.height} → ${doubled.height})")
    }

    @Test
    fun theSharedSyncOffsetMovesWhichCueIsOnScreen() {
        awaitCue(SPOKEN)
        // Earlier by a second: the late cue now starts before the playhead.
        compose.runOnUiThread { controller.get().playerViewModel.nudgeSubtitleOffset(-10) }
        compose.waitForIdle()
        compose.onNodeWithText("$SPOKEN\n$LATE").assertExists()
    }

    private fun awaitCue(text: String): DpRect {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        return compose.onNodeWithText(text, substring = true).getBoundsInRoot()
    }

    private fun assertInsideOverscan(
        cue: DpRect,
        screen: DpRect,
    ) {
        assertTrue(cue.left >= screen.left + Overscan.horizontal - 0.5.dp, "cue starts at ${cue.left}")
        assertTrue(cue.right <= screen.right - Overscan.horizontal + 0.5.dp, "cue ends at ${cue.right}")
        assertTrue(cue.bottom <= screen.bottom - Overscan.vertical + 0.5.dp, "cue bottom at ${cue.bottom}")
    }

    private companion object {
        const val SPOKEN = "We have to go back."
        const val LATE = "Not yet."
    }
}

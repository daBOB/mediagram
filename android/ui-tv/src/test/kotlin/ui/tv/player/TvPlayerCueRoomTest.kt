package ui.tv.player

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpRect
import data.CatalogRepository
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
import player.setSubtitleSize
import ui.tv.catalog.set
import kotlin.test.assertTrue

/**
 * Where a long cue goes when the controls come up: lifted clear of them
 * only as far as the title along the top allows — never printed over it —
 * and, with the settings panel open, into what the panel leaves of the
 * picture rather than under it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvPlayerCueRoomTest : TvPlayerScreenHarness() {
    override val run = THREE_TITLE_RUN

    override fun makeFixture(): TvPlayerFixture {
        val subtitled =
            set("set-one", Kind.EPISODE, "Pilot", show = "A Show", addedAt = 1, episode = 4, durationSecs = 600)
                .copy(subtitleLanguages = listOf("en"))
        val catalog = mockk<CatalogRepository>(relaxed = true)
        coEvery { catalog.mediaSet("set-one") } returns subtitled
        coEvery { catalog.mediaSet("set-two") } returns set("set-two", Kind.EPISODE, "After", show = "A Show", addedAt = 1, episode = 5, durationSecs = 600)
        val subtitles = mockk<SubtitleTrackSource>()
        coEvery { subtitles.load("set-one", "en") } returns listOf(TimedCue(40_000, 50_000, THREE_LINES), TimedCue(585_000, 600_000, CLOSING))
        return TvPlayerFixture(catalog = catalog, subtitles = subtitles)
    }

    @Test
    fun aThreeLineCueLiftedByTheControlsStaysBelowTheTitle() {
        compose.runOnUiThread { controller.get().playerViewModel.setSubtitleSize(200) }
        val cue = awaitCue()
        val top = compose.onNodeWithTag(TvTopBandTag).getBoundsInRoot()
        assertTrue(cue.top >= top.bottom, "cue starts at ${cue.top}, the title ends at ${top.bottom}")
    }

    @Test
    fun withThePanelOpenTheCueStaysLeftOfIt() {
        awaitCue()
        openSettings()
        compose.waitForIdle()
        val panel = compose.onNodeWithTag(TvSettingsPanelTag).getBoundsInRoot()
        val cue = compose.onNodeWithText(THREE_LINES).getBoundsInRoot()
        assertTrue(cue.right <= panel.left, "cue ends at ${cue.right}, the panel starts at ${panel.left}")
    }

    @Test
    fun whileTheUpNextCardShowsTheCueLiftsClearAboveIt() {
        awaitCue()
        nearTheEnd()
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText(CLOSING).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        val card = compose.onNodeWithTag(TvUpNextCardTag).getBoundsInRoot()
        val cue = compose.onNodeWithText(CLOSING).getBoundsInRoot()
        assertTrue(cue.bottom <= card.top, "cue ends at ${cue.bottom}, the card starts at ${card.top}")
    }

    private fun awaitCue(): DpRect {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(THREE_LINES).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        return compose.onNodeWithText(THREE_LINES).getBoundsInRoot()
    }

    private companion object {
        const val CLOSING = "And that is where the long closing line of this episode runs on past the middle."
        const val THREE_LINES = "The first line of it,\nthe second line of it,\nand the third."
    }
}

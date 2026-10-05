package ui.tv.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Back on a television, one thing at a time, front to back: an open menu,
 * then the episode list, then the statistics, then the controls — and only
 * then is the title left. Each close hands the remote back to what opened it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvPlayerBackOrderTest : TvPlayerScreenHarness() {
    override val run = THREE_TITLE_RUN

    override fun makeFixture() = runFixture()

    @Test
    fun backClosesTheEpisodesThenAMenuThenTheStatisticsThenTheControls() {
        toTransport(hasContentDescription("Stats"))
        press(Key.DirectionCenter)
        compose.onNodeWithTag(TvStatsOverlayTag).assertExists()

        along(hasContentDescription("Episodes"))
        press(Key.DirectionCenter)
        compose.onNodeWithTag(TvEpisodeSidebarTag).assertExists()
        back()
        compose.onNodeWithTag(TvEpisodeSidebarTag).assertDoesNotExist()
        compose.onNodeWithContentDescription("Episodes").assertIsFocused()
        compose.onNodeWithTag(TvStatsOverlayTag).assertExists()

        press(Key.DirectionUp)
        along(hasContentDescription("Speed"))
        press(Key.DirectionCenter)
        compose.onNodeWithTag(TvCardMenuTag).assertExists()
        back()
        compose.onNodeWithTag(TvCardMenuTag).assertDoesNotExist()
        compose.onNodeWithContentDescription("Speed").assertIsFocused()
        compose.onNodeWithTag(TvStatsOverlayTag).assertExists()

        back()
        compose.onNodeWithTag(TvStatsOverlayTag).assertDoesNotExist()
        compose.onNodeWithTag(TvSeekBarTag).assertExists()

        back()
        compose.onNodeWithTag(TvSeekBarTag).assertDoesNotExist()
        verify(exactly = 0) { fixture.media.stop() }

        back()
        compose.onNodeWithText("Library").assertExists()
    }
}

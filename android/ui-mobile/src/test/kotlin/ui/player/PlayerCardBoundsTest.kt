package ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.exoplayer.ExoPlayer
import io.mockk.mockk
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import player.PlayerChoices
import player.PlayerViewModel
import player.UpNextUiState
import player.together.TogetherUi

/**
 * The subtitles and the up-next card clear the card's top edge, read from its
 * bounds. Those stay the card's while it fades out, so nothing drops onto a
 * card still on screen, and go once it has left.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp")
class PlayerCardBoundsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun theBoundsStayThroughTheFadeAndGoAfterIt() {
        val card = PlayerCardState()
        var shown by mutableStateOf(true)
        val player = mockk<ExoPlayer>(relaxed = true)
        val viewModel = mockk<PlayerViewModel>(relaxed = true)
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                PlayerCardLayer(
                    shown = shown, player = player, viewModel = viewModel, card = card, choices = PlayerChoices(),
                    upNext = UpNextUiState(), statsShown = false, hasEpisodes = false, catalogedDurationSecs = null,
                    onToggleStats = {}, onEnterPip = null, onScrubbingChanged = {}, together = TogetherUi(),
                )
            }
        }
        compose.waitForIdle()
        assertNotNull(card.bounds)

        compose.mainClock.autoAdvance = false
        shown = false
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(100)
        assertNotNull(card.bounds)

        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertNull(card.bounds)
    }
}

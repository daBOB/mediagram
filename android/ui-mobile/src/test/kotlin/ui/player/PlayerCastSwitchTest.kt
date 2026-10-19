package ui.player

import android.app.PictureInPictureParams
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import data.CastAnswer
import data.CastAnswer.Answer
import data.LiveSession
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import model.together.CastWords
import model.together.Member
import model.together.Playback
import model.together.offered
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testing.FakeTogetherLive
import kotlin.test.assertEquals

/**
 * Play on TV from the player: once the TV takes the title, the phone stops
 * playing it and keeps where it was, as closing the player does, before the
 * remote takes the player's place.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp")
class PlayerCastSwitchTest : PlayerCardScreenBase() {
    @Test
    fun aCastTakenInPictureInPictureStopsThePlayerAndSavesWhereItWas() {
        val tv = CompletableDeferred<CastAnswer>()
        val live = FakeTogetherLive().apply { onOffer = { tv.await() } }
        PlayerTestActivity.casts.clear()
        open(together = live)
        fixture.positionMs = 300_000L

        compose.onNodeWithText(CastWords.PLAY_ON_TV).performClick()
        compose.onNodeWithText(CastWords.ASKING).assertExists()
        // Home while the TV is asked: a playing title shrinks into picture-in-picture on its own.
        compose.runOnUiThread { controller.get().enterPictureInPictureMode(PictureInPictureParams.Builder().build()) }
        val taken = offered("s", Member("tab", "Tablet", remote = true), Playback("set-one", true, 300.0, 1.0), "p1", 0L)
        compose.runOnUiThread { tv.complete(CastAnswer(Answer.ON, LiveSession(11, taken), "")) }
        compose.waitForIdle()

        assertEquals(listOf(11), PlayerTestActivity.casts)
        verify { fixture.media.stop() }
        assertEquals(300.0, fixture.repository.snapshot.value.progress.single { it.setId == "set-one" }.at)
    }
}

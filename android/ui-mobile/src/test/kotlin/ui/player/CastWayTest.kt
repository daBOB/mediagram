package ui.player

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.provider.Settings
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.common.PlaybackParameters
import data.CastAnswer
import data.TOGETHER_UNREACHABLE
import io.mockk.coVerify
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import model.together.CastWords
import model.together.Playback
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import testing.FakeTogetherLive
import ui.catalog.CastWayWords
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Play on TV in the player, where the system's Cast screen opens: it asks
 * whether the TV box plays the title, as Play on TV always did, or this
 * device plays it and its picture goes to a TV screen. While it does, the
 * tool names that screen and opens the Cast screen to disconnect.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w800dp-h600dp")
class CastWayTest : PlayerPresentationBase() {
    private val app get() = RuntimeEnvironment.getApplication()

    /** The system's Cast screen, as the tablet's Settings has it. */
    private fun castScreen() {
        val settings = ApplicationInfo().apply { packageName = "com.android.settings" }
        val screen = ActivityInfo().apply { packageName = settings.packageName; name = "com.android.settings.CastSettings"; applicationInfo = settings }
        shadowOf(app.packageManager).addResolveInfoForIntent(Intent(Settings.ACTION_CAST_SETTINGS), ResolveInfo().apply { activityInfo = screen })
    }

    private fun startedAction(): String? = shadowOf(app).nextStartedActivity?.action

    /** A TV that has not answered yet, so the offer stays asked. */
    private fun waitingTv() = FakeTogetherLive().apply { onOffer = { CompletableDeferred<CastAnswer>().await() } }

    private fun pressPlayOnTv() = compose.onNodeWithText(CastWords.PLAY_ON_TV).performClick()

    @Test
    fun playOnTvAsksWhichWay() {
        castScreen()
        val live = waitingTv()
        open(together = live)

        pressPlayOnTv()

        compose.onNode(hasText(CastWords.PLAY_ON_TV) and hasAnyAncestor(isDialog())).assertExists()
        compose.onNodeWithText(CastWayWords.THE_TV).assertIsFocused()
        compose.onNodeWithText(CastWayWords.THE_TV_DETAIL).assertExists()
        compose.onNodeWithText(CastWayWords.THIS_DEVICE).assertIsEnabled()
        compose.onNodeWithText(CastWayWords.THIS_DEVICE_DETAIL).assertExists()
        compose.onNodeWithText(CastWords.CANCEL).performClick()
        compose.onNodeWithText(CastWayWords.THE_TV).assertDoesNotExist()
        assertEquals(emptyList(), live.casts)
        assertNull(startedAction())
    }

    @Test
    fun theTvPlaysItOffersAsBefore() {
        castScreen()
        val live = spyk(waitingTv())
        open(together = live)
        fixture.positionMs = 300_000L
        every { fixture.media.playWhenReady } returns true
        every { fixture.media.playbackParameters } returns PlaybackParameters(1.5f)

        pressPlayOnTv()
        compose.onNodeWithText(CastWayWords.THE_TV).performClick()

        compose.onNodeWithText(CastWayWords.THE_TV).assertDoesNotExist()
        compose.onNodeWithText(CastWords.ASKING).assertExists()
        coVerify { live.offer(any(), Playback("set-one", playing = true, pos = 300.0, rate = 1.5), any(), any()) }
        assertNull(startedAction())
    }

    @Test
    fun thisDevicePlaysItOpensTheSystemCastScreen() {
        castScreen()
        val live = waitingTv()
        open(together = live)

        pressPlayOnTv()
        compose.onNodeWithText(CastWayWords.THIS_DEVICE).performClick()

        assertEquals("android.settings.CAST_SETTINGS", startedAction())
        compose.onNodeWithText(CastWayWords.THIS_DEVICE).assertDoesNotExist()
        assertEquals(emptyList(), live.casts)
        // The player keeps its title and place: the picture moves once the TV screen connects.
        verify(exactly = 0) { fixture.media.stop() }
    }

    @Test
    fun aGreyedTvChoiceSaysWhyAndTheOtherStillWorks() {
        castScreen()
        val live = FakeTogetherLive(available = false)
        open(together = live)

        // The tool itself is not greyed: this device playing touches no channel.
        compose.onNode(hasContentDescription(CastWords.PLAY_ON_TV)).assertIsEnabled().performClick()
        compose
            .onNode(hasContentDescription("${CastWayWords.THE_TV}, $TOGETHER_UNREACHABLE, ${CastWayWords.THE_TV_DETAIL}"))
            .assertIsNotEnabled()
            .performClick()
        assertEquals(emptyList(), live.casts)

        compose.onNodeWithText(CastWayWords.THIS_DEVICE).assertIsEnabled().performClick()
        assertEquals("android.settings.CAST_SETTINGS", startedAction())
    }

    @Test
    fun withoutTheSystemCastScreenPlayOnTvOffersStraightAway() {
        val live = waitingTv().apply { available.value = false }
        open(together = live)

        compose.onNode(hasContentDescription("${CastWords.PLAY_ON_TV}, $TOGETHER_UNREACHABLE")).assertIsNotEnabled()
        compose.runOnUiThread { live.available.value = true }
        pressPlayOnTv()

        compose.onNodeWithText(CastWayWords.THE_TV).assertDoesNotExist()
        compose.onNodeWithText(CastWords.ASKING).assertExists()
        assertEquals(1, live.casts.count { it.startsWith("offer ") })
    }

    @Test
    fun whilePresentingTheToolSaysPlayingOnTheTvAndOpensTheCastScreen() {
        castScreen()
        val live = waitingTv()
        open(together = live)
        connectTv()

        compose.onNodeWithText(CastWords.PLAY_ON_TV).assertDoesNotExist()
        // The top bar says it too, in capitals; this is the card's tool.
        compose.onNodeWithText(label).performClick()

        assertEquals("android.settings.CAST_SETTINGS", startedAction())
        compose.onNodeWithText(CastWayWords.THE_TV).assertDoesNotExist()
        assertEquals(emptyList(), live.casts)
    }
}

package ui.tv.player

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import data.TOGETHER_UNREACHABLE
import io.mockk.every
import io.mockk.mockk
import model.Kind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import player.together.TogetherButton
import player.together.TogetherUi
import ui.tv.catalog.TvScreenStateTest
import ui.tv.catalog.set
import kotlin.test.assertEquals

/** Watch together ends the card's tools, and the remote's OK on it reaches the player, which says what came of it over the picture. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvTogetherToolTest : TvPlayerScreenHarness() {
    @Test
    fun watchTogetherFollowsFramingAndEndsTheRow() {
        toTool(hasContentDescription("Framing"))
        press(Key.DirectionRight)
        compose.onNode(hasContentDescription("Watch together")).assertIsFocused()
        press(Key.DirectionRight)
        compose.onNode(hasContentDescription("Watch together")).assertIsFocused()
    }

    @Test
    fun okOnWatchTogetherAsksThePlayerAndSaysWhatCameOfIt() {
        compose.waitUntil(timeoutMillis = 5_000) { controller.get().playerViewModel.together.value.enabled }
        toTool(hasContentDescription("Watch together"))
        press(Key.DirectionCenter)
        // This fixture's Telegram refuses every start.
        compose.onNodeWithText(TOGETHER_UNREACHABLE).assertIsDisplayed()
    }
}

/** The tool, label and notice as drawn from what the player decided. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvTogetherViewTest : TvScreenStateTest() {
    private var pressed = 0

    private fun extras(ui: TogetherUi) =
        TvPlayerExtras(
            marks = null,
            markActions = TvMarksActions({}, {}, {}),
            statsShown = false,
            onToggleStats = {},
            totals = { error("no statistics here") },
            together = ui,
            onTogether = { pressed++ },
        )

    /** A television has no hover for the web's title: greyed out, the tool still takes the remote and says why. */
    @Test
    fun greyedOutTheToolTakesTheRemoteSaysWhyAndIgnoresOk() {
        val focus = TvPlayerFocus()
        show { Row { TvCardTools(focus, extras(TogetherUi(enabled = false, reason = TOGETHER_UNREACHABLE)), TvStageBands(), speed = 1f) } }
        compose.runOnUiThread { focus.together.requestFocus() }
        compose.waitForIdle()

        compose.onNode(hasContentDescription("Watch together, $TOGETHER_UNREACHABLE")).assertIsFocused()
        compose.onNodeWithText(TOGETHER_UNREACHABLE).assertIsDisplayed()
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(0, pressed)
    }

    @Test
    fun inASessionTheToolLeaves() {
        val focus = TvPlayerFocus()
        show { Row { TvCardTools(focus, extras(TogetherUi(button = TogetherButton.LEAVE, enabled = true)), TvStageBands(), speed = 1f) } }
        compose.runOnUiThread { focus.together.requestFocus() }
        compose.onNodeWithText("Leave watch together").assertIsDisplayed()
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(1, pressed)
    }

    @Test
    fun theLabelNamesWhoIsInTheSessionBesideTheTitle() {
        show {
            TvPlayerTopBar(
                set = set("a", Kind.MOVIE, "A Film", addedAt = 0),
                marks = null,
                markActions = TvMarksActions({}, {}, {}),
                onToggleNotes = null,
                focus = TvPlayerFocus(),
                label = "Together · TV, Tablet",
            )
        }
        compose.onNodeWithText("A Film").assertIsDisplayed()
        compose.onNodeWithText("TOGETHER · TV, TABLET").assertIsDisplayed()
    }

    @Test
    fun theControlsCarryThePlayersLabelToTheTopBar() {
        val player = mockk<Player>(relaxed = true) { every { playbackParameters } returns PlaybackParameters.DEFAULT }
        show {
            TvPlayerControls(
                player = player,
                set = set("a", Kind.MOVIE, "A Film", addedAt = 0),
                focus = TvPlayerFocus(),
                extras = extras(TogetherUi(button = TogetherButton.LEAVE, enabled = true, label = "Together · Tablet")),
                onSeekBarFocused = {},
                bands = TvStageBands(),
            )
        }
        compose.onNodeWithText("TOGETHER · TABLET").assertIsDisplayed()
        compose.onNodeWithText("Leave watch together").assertIsDisplayed()
    }

    @Test
    fun theNoticeSaysWhatAnotherScreenDidUntilThePlayerTakesItAway() {
        var notice: String? by mutableStateOf("Skipped to 41:20 by Tablet")
        show { TvTogetherNotice(notice) }
        compose.onNodeWithText("Skipped to 41:20 by Tablet").assertIsDisplayed()

        compose.runOnUiThread { notice = null }
        compose.waitForIdle()
        compose.onNodeWithText("Skipped to 41:20 by Tablet").assertDoesNotExist()
    }
}

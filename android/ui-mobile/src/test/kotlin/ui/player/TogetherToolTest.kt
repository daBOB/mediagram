package ui.player

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import data.TOGETHER_UNREACHABLE
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import player.PlayerChoices
import player.together.TogetherButton
import player.together.TogetherUi
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Watch together on the phone's player, as the web's control card, top bar
 * and `.together-note` draw it: one tool that says what it does, greyed out
 * with the reason; the "Together · TV, Tablet" label beside the title; and
 * what another screen just did, for as long as the player keeps it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w800dp-h600dp")
class TogetherToolTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    private var pressed = 0

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { MaterialTheme { content() } }
        }
        compose.waitForIdle()
    }

    private fun tools(ui: TogetherUi) =
        show {
            CardToolsRow(
                choices = PlayerChoices(),
                speed = 1f,
                actions =
                    PlayerCardActions(
                        onRestart = {}, onPrevious = {}, onNext = {}, onToggleSubtitles = {}, onOpenMenu = {},
                        onToggleStats = {}, onEpisodes = {}, onEnterPip = null, onTogether = { pressed++ },
                    ),
                together = ui,
            )
        }

    @Test
    fun theToolStartsASessionWhereNoneIsLive() {
        tools(TogetherUi(button = TogetherButton.START, enabled = true))
        compose.onNodeWithText("Watch together").assertIsEnabled().performClick()
        assertEquals(1, pressed)
    }

    @Test
    fun theToolJoinsTheSessionLiveElsewhere() {
        tools(TogetherUi(button = TogetherButton.JOIN, enabled = true))
        compose.onNodeWithText("Join").performClick()
        assertEquals(1, pressed)
    }

    @Test
    fun theToolLeavesTheSessionThisScreenIsIn() {
        tools(TogetherUi(button = TogetherButton.LEAVE, enabled = true, label = "Together · TV"))
        compose.onNodeWithText("Leave watch together").performClick()
        assertEquals(1, pressed)
    }

    @Test
    fun greyedOutTheToolSaysWhyAndDoesNothing() {
        tools(TogetherUi(button = TogetherButton.START, enabled = false, reason = TOGETHER_UNREACHABLE))
        compose
            .onNode(hasContentDescription("Watch together, Telegram isn't reachable"))
            .assertIsNotEnabled()
            .performClick()
        assertEquals(0, pressed)
    }

    /** At the web's 768px and wider the label shares the title's line; a screen reader hears it as written, not in capitals. */
    @Test
    fun onAWideBarTheLabelNamesWhoIsInTheSessionBesideTheTitle() {
        show { PlayerTopBar(title = "A Film", showTitle = true, onBack = {}, label = "Together · TV, Tablet") }
        compose.onNodeWithText("TOGETHER · TV, TABLET").assertExists()
        val label = compose.onNode(hasContentDescription("Together · TV, Tablet")).getUnclippedBoundsInRoot()
        val title = compose.onNodeWithText("A Film").getUnclippedBoundsInRoot()
        assertTrue(label.top < title.bottom && label.left >= title.right, "the label ($label) sits beside the title ($title)")
    }

    @Test
    @Config(qualifiers = "w400dp-h800dp")
    fun onANarrowBarTheLabelTakesTheLineUnderTheTitle() {
        show { PlayerTopBar(title = "A Film", showTitle = true, onBack = {}, label = "Together · TV, Tablet") }
        val label = compose.onNode(hasContentDescription("Together · TV, Tablet")).getUnclippedBoundsInRoot()
        val title = compose.onNodeWithText("A Film").getUnclippedBoundsInRoot()
        assertTrue(label.top >= title.bottom, "the label ($label) sits under the title ($title)")
    }

    @Test
    fun theTopChromeDrawsThePlayersLabelAndNotice() {
        show {
            PlayerTopChrome(
                openSet = null, barShown = true, statsShown = false, isInPip = false, player = null,
                totals = { error("no statistics here") }, onBack = {},
                together = TogetherUi(button = TogetherButton.LEAVE, enabled = true, label = "Together · TV", notice = "TV joined"),
            )
        }
        compose.onNodeWithText("TOGETHER · TV").assertExists()
        compose.onNodeWithText("TV joined").assertExists()
    }

    @Test
    fun outOfASessionThereIsNoLabel() {
        show { PlayerTopBar(title = "A Film", showTitle = true, onBack = {}, label = null) }
        compose.onNodeWithText("TOGETHER", substring = true).assertDoesNotExist()
    }

    @Test
    fun theNoticeSaysWhatAnotherScreenDidUntilThePlayerTakesItAway() {
        var notice: String? by mutableStateOf("Paused by TV")
        show { TogetherNoticePill(notice) }
        compose.onNodeWithText("Paused by TV").assertExists()

        compose.runOnUiThread { notice = null }
        compose.waitForIdle()
        compose.onNodeWithText("Paused by TV").assertDoesNotExist()
    }
}

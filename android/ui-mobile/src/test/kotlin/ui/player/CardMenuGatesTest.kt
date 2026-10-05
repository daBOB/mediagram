package ui.player

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.unit.dp
import playback.AudioOption
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.After
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import playback.Framing
import player.PLAYBACK_SPEEDS
import player.PlayerChoices
import player.SUBTITLES_OFF
import player.SubtitleOption
import player.speedLabel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What each card menu offers and what choosing in it does. The subtitle
 * menu's language rows follow a regular track and its Style… row any track
 * that can show; a title with neither never opens it (the card disables ▾).
 * Choosing closes a menu — except the style panel, which is adjusted a step
 * at a time against the film.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CardMenuGatesTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    private val chosen = mutableListOf<String>()
    private var opened: CardMenu? = null
    private var done = 0

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private val english = listOf(SubtitleOption(SUBTITLES_OFF, "Off", true), SubtitleOption("en", "English", false))

    private fun show(
        menu: CardMenu,
        choices: PlayerChoices,
    ) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MaterialTheme {
                    CardMenuPanel(
                        menu = menu,
                        choices = choices,
                        actions =
                            CardMenuActions(
                                onSpeed = { chosen += "speed=$it" },
                                onAudio = { chosen += "audio=${it.text}" },
                                onSubtitle = { chosen += "subtitle=$it" },
                                onSize = { chosen += "size=$it" },
                                onBacking = { chosen += "backing=$it" },
                                onNudge = { chosen += "nudge=$it" },
                                onResetOffset = { chosen += "reset" },
                                onFraming = { chosen += "framing=${it.stored}" },
                            ),
                        onOpen = { opened = it },
                        onDone = { done++ },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aForcedOnlyTitleOffersStyleButNoLanguageRows() {
        show(CardMenu.Subtitles, PlayerChoices(subtitleStyleVisible = true))

        compose.onNodeWithText("Style…").assertExists()
        compose.onNodeWithText("Subtitles").assertDoesNotExist()
    }

    @Test
    fun aTitleWithARegularTrackOffersItsLanguagesOffAndStyle() {
        show(CardMenu.Subtitles, PlayerChoices(subtitleOptions = english, subtitleStyleVisible = true))

        compose.onNodeWithText("Off").assertExists()
        compose.onNodeWithText("English").assertExists()
        compose.onNodeWithText("Style…").assertExists()
    }

    @Test
    fun choosingALanguageChoosesItAndCloses() {
        show(CardMenu.Subtitles, PlayerChoices(subtitleOptions = english, subtitleStyleVisible = true))

        compose.onNodeWithText("English").performClick()

        assertEquals(listOf("subtitle=en"), chosen)
        assertEquals(1, done)
    }

    @Test
    fun styleOpensTheStylePanelInTheSameMenu() {
        show(CardMenu.Subtitles, PlayerChoices(subtitleOptions = english, subtitleStyleVisible = true))

        compose.onNodeWithText("Style…").performClick()

        assertEquals(CardMenu.SubtitleStyle, opened)
        assertEquals(0, done)
    }

    @Test
    fun theStylePanelStaysOpenWhileItIsAdjusted() {
        show(CardMenu.SubtitleStyle, PlayerChoices(subtitleStyleVisible = true))

        compose.onNodeWithText("Subtitle style").assertExists()
        compose.onNodeWithContentDescription("Subtitles later").performScrollTo().performClick()

        assertEquals(listOf("nudge=1"), chosen)
        assertEquals(0, done)
    }

    @Test
    fun theSpeedMenuListsEverySpeedWithTheCurrentOneSelected() {
        show(CardMenu.Speed, PlayerChoices(speed = 1.25f))

        for (speed in PLAYBACK_SPEEDS) compose.onNodeWithText(speedLabel(speed)).assertExists()
        compose.onNodeWithText("1.25×").assertIsSelected()
        compose.onNodeWithText("1×").assertIsNotSelected()
    }

    @Test
    fun choosingASpeedChoosesItAndCloses() {
        show(CardMenu.Speed, PlayerChoices())

        compose.onNodeWithText("1.5×").performClick()

        assertEquals(listOf("speed=1.5"), chosen)
        assertEquals(1, done)
    }

    @Test
    fun theFramingMenuOffersFitFillAndTheTwoShapes() {
        show(CardMenu.Framing, PlayerChoices())

        for (framing in Framing.entries) compose.onNodeWithText(framing.label).assertExists()
        compose.onNodeWithText("Fill").performClick()

        assertEquals(listOf("framing=fill"), chosen)
        assertEquals(1, done)
    }

    @Test
    fun everyMenuRowMeetsTheTouchTargetFloor() {
        val twoTracks = listOf(AudioOption(0, 0, "en", "English", true), AudioOption(1, 0, "de", "German", false))
        val choices = PlayerChoices(audioOptions = twoTracks, subtitleOptions = english, subtitleStyleVisible = true)
        val rows =
            listOf(
                CardMenu.Speed to "1×",
                CardMenu.Audio to "German",
                CardMenu.Framing to "Fill",
                CardMenu.Subtitles to "English",
                CardMenu.Subtitles to "Style…",
                CardMenu.SubtitleStyle to "Reset",
            )
        for ((menu, label) in rows) {
            show(menu, choices)
            compose.onNodeWithText(label).assertHeightIsAtLeast(48.dp)
            compose.runOnUiThread { controller.close() }
        }
    }
}

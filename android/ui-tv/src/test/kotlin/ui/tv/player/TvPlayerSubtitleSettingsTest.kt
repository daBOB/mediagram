package ui.tv.player

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import data.CatalogRepository
import io.mockk.coEvery
import io.mockk.mockk
import model.Kind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import playback.SubtitleTrackSource
import playback.TimedCue
import player.SUBTITLES_OFF
import ui.tv.catalog.set
import kotlin.test.assertEquals

/**
 * The settings panel on a title with English subtitles: the two subtitle
 * sections join it, with the phone's rows, and what they choose is the
 * shared choice the picture's own subtitles are drawn from.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvPlayerSubtitleSettingsTest : TvPlayerScreenHarness() {
    override fun makeFixture(): TvPlayerFixture {
        val subtitled =
            set("set-one", Kind.EPISODE, "Pilot", show = "A Show", addedAt = 1, episode = 4, durationSecs = 600)
                .copy(subtitleLanguages = listOf("en"))
        val catalog = mockk<CatalogRepository>(relaxed = true)
        coEvery { catalog.mediaSet("set-one") } returns subtitled
        val subtitles = mockk<SubtitleTrackSource>()
        coEvery { subtitles.load("set-one", "en") } returns listOf(TimedCue(40_000, 50_000, "Hello."))
        return TvPlayerFixture(catalog = catalog, subtitles = subtitles)
    }

    @Test
    fun bothSubtitleSectionsAppearWithThePhonesRows() {
        openWithSubtitles()
        for (text in listOf("Subtitles", "Off", "English", "Subtitle style", "Small", "Normal", "Large", "Larger", "Shadow", "Box", "None", "Sync")) {
            inPanel(text).assertExists()
        }
        inPanel("English").assertIsSelected()
        inPanel("Normal").assertIsSelected()
        inPanel("Shadow").assertIsSelected()
    }

    @Test
    fun sizeAndBackingChosenAreTheSharedChoices() {
        openWithSubtitles()
        inPanel("Larger").performSemanticsAction(SemanticsActions.OnClick)
        inPanel("Box").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        val choices = controller.get().playerViewModel.choices.value
        assertEquals(135, choices.subtitleSizePercent)
        assertEquals("box", choices.subtitleBacking)
    }

    @Test
    fun theSyncRowNudgesAndResets() {
        openWithSubtitles()
        compose.onNodeWithContentDescription("Subtitles later").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        inPanel("+0.1s").assertExists()
        assertEquals(100L, controller.get().playerViewModel.choices.value.subtitleOffsetMs)

        inPanel("Reset").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        inPanel("0.0s").assertExists()
    }

    @Test
    fun offTurnsTheSubtitlesOff() {
        openWithSubtitles()
        inPanel("Off").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        val chosen = controller.get().playerViewModel.choices.value.subtitleOptions.single { it.selected }
        assertEquals(SUBTITLES_OFF, chosen.value)
        compose.onNodeWithText("Hello.").assertDoesNotExist()
    }

    private fun openWithSubtitles() {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Hello.").fetchSemanticsNodes().isNotEmpty()
        }
        openSettings()
    }

    private fun inPanel(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(TvSettingsPanelTag)))
}

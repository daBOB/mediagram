package ui.tv.player

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import data.CatalogRepository
import designsystem.Spacing
import io.mockk.coEvery
import io.mockk.mockk
import model.Kind
import model.SubtitleTrackInfo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import playback.SubtitleTrackSource
import playback.TimedCue
import player.SUBTITLES_OFF
import ui.tv.catalog.set
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * CC ▾ on a title with one regular English subtitle track: the languages
 * and Off, Off chosen by default — nothing is remembered or preferred for
 * this show — then "Style…", which opens the style menu in the options'
 * place. What a row chosen by hand picks is the shared choice the
 * picture's own subtitles are drawn from.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
// Real text measurement: the sync row's fit is a question of how wide its words draw.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvPlayerSubtitleSettingsTest : TvPlayerScreenHarness() {
    override fun makeFixture(): TvPlayerFixture {
        val subtitled =
            set("set-one", Kind.EPISODE, "Pilot", show = "A Show", addedAt = 1, episode = 4, durationSecs = 600)
                .copy(subtitles = listOf(ENGLISH_TRACK))
        val catalog = mockk<CatalogRepository>(relaxed = true)
        coEvery { catalog.mediaSet("set-one") } returns subtitled
        val subtitles = mockk<SubtitleTrackSource>()
        coEvery { subtitles.load("set-one", ENGLISH_TRACK.track) } returns listOf(TimedCue(40_000, 50_000, "Hello."))
        return TvPlayerFixture(catalog = catalog, subtitles = subtitles)
    }

    @Test
    fun theOptionsOfferOffAndEnglishWithOffChosenThenStyle() {
        openOptions()
        for (text in listOf("Off", "English", "Style…")) inMenu(text).assertExists()
        inMenu("Subtitles").assertDoesNotExist()
        inMenu("Off").assertIsSelected()
        inMenu("Off").assertIsFocused()
        compose.onNodeWithText("Hello.").assertDoesNotExist()
    }

    @Test
    fun styleTakesTheOptionsPlaceOnTheCurrentSize() {
        openStyle()
        for (text in listOf("Subtitle style", "Small", "Normal", "Large", "Larger", "Shadow", "Box", "None", "Sync")) {
            inMenu(text).assertExists()
        }
        inMenu("Normal").assertIsSelected()
        inMenu("Normal").assertIsFocused()
        inMenu("Shadow").assertIsSelected()
        // One menu at a time: the options gave way rather than standing under it.
        compose.onAllNodesWithTag(TvCardMenuTag).assertCountEquals(1)
        inMenu("English").assertDoesNotExist()
    }

    @Test
    fun choosingEnglishClosesTheMenuTurnsCcOnAndShowsItsCue() {
        openOptions()

        inMenu("English").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        compose.onNodeWithTag(TvCardMenuTag).assertDoesNotExist()
        compose.onNodeWithText("CC ●").assertExists()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Hello.").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun sizeAndBackingChosenAreTheSharedChoicesAndTheStyleStaysOpen() {
        openStyle()
        inMenu("Larger").performSemanticsAction(SemanticsActions.OnClick)
        inMenu("Box").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        val choices = controller.get().playerViewModel.choices.value
        assertEquals(135, choices.subtitleSizePercent)
        assertEquals("box", choices.subtitleBacking)
        compose.onNodeWithTag(TvCardMenuTag).assertExists()
    }

    @Test
    fun theSyncRowNudgesAndResets() {
        openStyle()
        compose.onNodeWithContentDescription("Subtitles later").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        inMenu("+0.1s").assertExists()
        assertEquals(100L, controller.get().playerViewModel.choices.value.subtitleOffsetMs)

        inMenu("Reset").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        inMenu("0.0s").assertExists()
    }

    @Test
    fun offTurnsTheSubtitlesOff() {
        openOptions()
        inMenu("English").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Hello.").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithContentDescription("Subtitle options").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        inMenu("Off").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        val chosen = controller.get().playerViewModel.choices.value.subtitleOptions.single { it.selected }
        assertEquals(SUBTITLES_OFF, chosen.value)
        compose.onNodeWithText("Hello.").assertDoesNotExist()
    }

    @Test
    fun theSyncButtonsFitInsideTheMenuOnOneLine() {
        openStyle()
        // Bounds are clipped to what the menu's scroll shows; bring the row into it first.
        compose.onNodeWithTag(TvSyncButtonsTag).performScrollTo()
        val menu = compose.onNodeWithTag(TvCardMenuTag).getBoundsInRoot()
        val row = compose.onNodeWithTag(TvSyncButtonsTag).getBoundsInRoot()
        val earlier = compose.onNodeWithContentDescription("Subtitles earlier").getBoundsInRoot()
        val reset = inMenu("Reset").getBoundsInRoot()

        assertTrue(reset.right <= menu.right - Spacing.medium + 0.5.dp, "Reset ends at ${reset.right}, the menu's margin at ${menu.right - Spacing.medium}")
        assertTrue(row.right <= menu.right - Spacing.medium + 0.5.dp, "the row ends at ${row.right}")
        // Squeezed, "Reset" would wrap onto a second line and stand taller than "−".
        assertTrue(reset.height <= earlier.height + 0.5.dp, "Reset is ${reset.height} tall, − is ${earlier.height}")
        assertTrue(reset.width > earlier.width, "Reset is ${reset.width} wide, − is ${earlier.width}")
    }

    /** Opens CC ▾ once the set's own track is known — a regular track is what puts language rows in it. */
    private fun openOptions() {
        compose.waitUntil(timeoutMillis = 5_000) {
            controller.get().playerViewModel.choices.value.subtitleOptions.isNotEmpty()
        }
        openMenu("Subtitle options")
    }

    private fun openStyle() {
        openOptions()
        inMenu("Style…").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun inMenu(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(TvCardMenuTag)))

    private companion object {
        val ENGLISH_TRACK = SubtitleTrackInfo(track = 0, lang = "en", forced = false, sdh = false, label = "")
    }
}

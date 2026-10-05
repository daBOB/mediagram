package ui.tv.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import data.CatalogRepository
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
import ui.tv.catalog.set

private fun fixtureWith(tracks: List<SubtitleTrackInfo>): TvPlayerFixture {
    val titled =
        set("set-one", Kind.EPISODE, "Pilot", show = "A Show", addedAt = 1, episode = 4, durationSecs = 600)
            .copy(subtitles = tracks)
    val catalog = mockk<CatalogRepository>(relaxed = true)
    coEvery { catalog.mediaSet("set-one") } returns titled
    return TvPlayerFixture(catalog = catalog, subtitles = mockk<SubtitleTrackSource>(relaxed = true))
}

private fun menuText(text: String) = hasText(text) and hasAnyAncestor(hasTestTag(TvCardMenuTag))

/**
 * A title whose only track is forced: CC is there but dimmed — nothing
 * regular to turn on — and its ▾ opens straight onto "Style…", since size
 * and sync still apply to the forced lines.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvPlayerForcedOnlySubtitleGateTest : TvPlayerScreenHarness() {
    override fun makeFixture() = fixtureWith(listOf(SubtitleTrackInfo(track = 0, lang = "de", forced = true, sdh = false, label = "")))

    @Test
    fun ccIsDimmedAndItsOptionsOpenOnStyle() {
        compose.waitUntil(timeoutMillis = 5_000) { controller.get().playerViewModel.choices.value.subtitleStyleVisible }
        compose.onNodeWithContentDescription("Subtitles").assertIsNotEnabled()

        openMenu("Subtitle options")

        compose.onNode(menuText("Off")).assertDoesNotExist()
        compose.onNode(menuText("Style…")).assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNode(menuText("Subtitle style")).assertExists()
        compose.onNode(menuText("Sync")).assertExists()
    }
}

/** A title with no subtitle track: CC and ▾ both dimmed, and ▾ opens nothing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvPlayerNoSubtitlesGateTest : TvPlayerScreenHarness() {
    override fun makeFixture() = fixtureWith(emptyList())

    @Test
    fun ccAndItsOptionsAreDimmedAndOpenNothing() {
        // The set has loaded once its title line is up; absence before that proves nothing.
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("A Show · S1E4 · Pilot").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Subtitles").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Subtitle options").assertIsNotEnabled()

        openMenu("Subtitle options")

        compose.onNodeWithTag(TvCardMenuTag).assertDoesNotExist()
    }
}

/** A title with a regular track: CC starts off and one press turns it on. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvPlayerCcButtonTest : TvPlayerScreenHarness() {
    override fun makeFixture() = fixtureWith(listOf(SubtitleTrackInfo(track = 0, lang = "en", forced = false, sdh = false, label = "")))

    @Test
    fun onePressTurnsTheSubtitlesOn() {
        compose.waitUntil(timeoutMillis = 5_000) { controller.get().playerViewModel.choices.value.ccVisible }
        compose.onNodeWithText("CC ○").assertExists()

        compose.onNodeWithContentDescription("Subtitles").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        compose.onNodeWithText("CC ●").assertExists()
    }
}
